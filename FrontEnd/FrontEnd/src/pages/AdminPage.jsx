import React, { useState, useEffect, useRef } from 'react';
import authHeader from '../services/auth-header';
import '../styles/AdminPage.css';
import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

const AdminPage = () => {
    const [tasks, setTasks] = useState([]);
    const [taskMessages, setTaskMessages] = useState({});
    const [runningTasks, setRunningTasks] = useState([]);
    const [pruningMessage, setPruningMessage] = useState('');
    const [isPruning, setIsPruning] = useState(false);
    const [pruningArchives, setPruningArchives] = useState([]);
    const [archiveMessage, setArchiveMessage] = useState('');
    const [isRestoringArchive, setIsRestoringArchive] = useState(null);
    const [backups, setBackups] = useState([]);
    const [backupMessage, setBackupMessage] = useState('');
    const [isRestoring, setIsRestoring] = useState(null);

    // Demo & WebSocket state
    const [keywords, setKeywords] = useState('data analytics');
    const [selectedApi, setSelectedApi] = useState('jsearch-it');
    const [isPopulating, setIsPopulating] = useState(false);
    const [progressEvents, setProgressEvents] = useState([]);
    const [showSummary, setShowSummary] = useState(false);
    const stompClientRef = useRef(null);
    const progressPanelRef = useRef(null);

    useEffect(() => {
        fetchTasks();
        fetchBackups();
        fetchPruningArchives();

        const socket = new SockJS('/ws');
        const client = Stomp.over(socket);
        client.debug = null;
        
        client.connect({}, () => {
            client.subscribe('/topic/demo-progress', (message) => {
                const event = JSON.parse(message.body);
                if (event.status === 'FINISHED') {
                    setIsPopulating(false);
                    setShowSummary(true);
                } else {
                    setProgressEvents(prev => [...prev, event]);
                }
            });
        }, (error) => {
            console.error("WebSocket connection error:", error);
        });

        stompClientRef.current = client;

        return () => {
            if (stompClientRef.current && stompClientRef.current.connected) {
                stompClientRef.current.disconnect();
            }
        };
    }, []);

    useEffect(() => {
        if (progressPanelRef.current) {
            progressPanelRef.current.scrollTop = progressPanelRef.current.scrollHeight;
        }
    }, [progressEvents]);

    const fetchTasks = async () => {
        try {
            const response = await fetch('/api/maintenance/tasks', { headers: authHeader() });
            if (!response.ok) throw new Error('Failed to fetch tasks');
            const availableTasks = await response.json();
            setTasks(availableTasks);
            if (availableTasks.length > 0) {
                setSelectedApi(availableTasks[0]);
            }
        } catch (error) {
            console.error("Error fetching tasks:", error);
        }
    };

    const fetchBackups = async () => {
        try {
            const response = await fetch('/api/backups', { headers: authHeader() });
            if (!response.ok) throw new Error('Failed to fetch backups');
            setBackups(await response.json());
        } catch (error) {
            setBackupMessage(`Error: ${error.message}`);
        }
    };

    const fetchPruningArchives = async () => {
        try {
            const response = await fetch('/api/maintenance/pruning-archives', { headers: authHeader() });
            if (!response.ok) throw new Error('Failed to fetch pruning archives');
            setPruningArchives(await response.json());
        } catch (error) {
            setArchiveMessage(`Error: ${error.message}`);
        }
    };

    const handleTriggerImport = async (taskName) => {
        if (runningTasks.includes(taskName)) return;
        setRunningTasks(prev => [...prev, taskName]);
        setTaskMessages(prev => ({ ...prev, [taskName]: `Starting task '${taskName}'...` }));
        try {
            const response = await fetch(`/api/maintenance/trigger-import/${taskName}`, { 
                method: 'POST',
                headers: authHeader() 
            });
            const responseText = await response.text();
            if (!response.ok) throw new Error(responseText || `Failed to trigger ${taskName}`);
            setTaskMessages(prev => ({ ...prev, [taskName]: responseText }));
        } catch (error) {
            setTaskMessages(prev => ({ ...prev, [taskName]: `Error: ${error.message}` }));
        } finally {
            setTimeout(() => setRunningTasks(prev => prev.filter(t => t !== taskName)), 3000);
        }
    };

    const handleTriggerPruning = async () => {
        if (isPruning) return;
        setIsPruning(true);
        setPruningMessage('Starting database pruning...');
        try {
            const response = await fetch('/api/maintenance/trigger-pruning', { 
                method: 'POST',
                headers: authHeader() 
            });
            const responseText = await response.text();
            if (!response.ok) throw new Error(responseText || 'Failed to trigger pruning');
            setPruningMessage(responseText);
        } catch (error) {
            setPruningMessage(`Error: ${error.message}`);
        } finally {
            setIsPruning(false);
            setTimeout(fetchPruningArchives, 5000); 
        }
    };

    const handleRestoreArchive = async (filename) => {
        const isConfirmed = window.confirm(`CONFIRM RESTORE\n\nThis will restore deleted jobs from the archive '${filename}'. Proceed?`);
        if (!isConfirmed) return;
        setIsRestoringArchive(filename);
        setArchiveMessage(`Starting restoration from ${filename}...`);
        try {
            const response = await fetch('/api/maintenance/restore-archive', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({ filename }),
            });
            const responseText = await response.text();
            if (!response.ok) throw new Error(responseText || 'Failed to restore archive');
            setArchiveMessage(responseText);
        } catch (error) {
            setArchiveMessage(`Error: ${error.message}`);
        } finally {
             setTimeout(() => setIsRestoringArchive(null), 3000);
        }
    };

    const handleLoadBackup = async (filename) => {
        const isConfirmed = window.confirm(`CONFIRM LOAD BACKUP\n\nThis will DELETE ALL EXISTING DATA in the database and replace it completely with the contents of '${filename}'. \n\nAre you absolutely sure you want to proceed? This cannot be undone.`);
        if (!isConfirmed) return;
        setIsRestoring(filename);
        setBackupMessage(`Clearing database and loading script ${filename}...`);
        try {
            const response = await fetch('/api/backups/load', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({ filename }),
            });
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || 'Failed to load backup');
            setBackupMessage(data.message);
        } catch (error) {
            setBackupMessage(`Error: ${error.message}`);
        } finally {
            setTimeout(() => setIsRestoring(null), 3000);
        }
    };

    const handlePopulateByKeywords = async () => {
        if (isPopulating || !keywords.trim()) return;
        setIsPopulating(true);
        setProgressEvents([]);
        setShowSummary(false);
        
        try {
            const response = await fetch('/api/maintenance/demo-populate', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({ 
                    apiSource: selectedApi,
                    keywords: keywords.trim() 
                }),
            });
            
            if (!response.ok) {
                setIsPopulating(false);
                throw new Error('Failed to start population task');
            }
        } catch (error) {
            setIsPopulating(false);
            setProgressEvents([{ status: 'ERROR', jobTitle: 'System', details: error.message }]);
        }
    };

    const getFlagForCountry = (countryCode) => {
        if (!countryCode) return '🌐';
        const code = countryCode.toUpperCase();
        const flags = {'GB': '🇬🇧', 'UK': '🇬🇧', 'US': '🇺🇸', 'USA': '🇺🇸', 'DE': '🇩🇪', 'FR': '🇫🇷', 'CA': '🇨🇦', 'AU': '🇦🇺', 'NL': '🇳🇱', 'IN': '🇮🇳', 'ES': '🇪🇸', 'IT': '🇮🇹', 'BR': '🇧🇷', 'PL': '🇵🇱', 'AT': '🇦🇹', 'CH': '🇨🇭', 'RO': '🇷🇴', 'FB': '🇧🇷'};
        if (flags[code]) return flags[code];
        if (code.length === 2) {
             try {
                const codePoints = code.split('').map(char => 127397 + char.charCodeAt());
                return String.fromCodePoint(...codePoints);
             } catch (e) { return '🌐'; }
        }
        return '🌐';
    };

    const renderTaskTitle = (taskName) => {
        if (taskName.startsWith('adzuna-')) {
            const countryCode = taskName.substring(7);
            return <h3><span className="flag-emoji">{getFlagForCountry(countryCode)}</span>{taskName}</h3>;
        } else if (taskName.startsWith('jsearch-')) {
            return <h3><span className="flag-emoji">🔍</span>{taskName}</h3>;
        } else if (taskName.startsWith('remotive-')) {
            return <h3><span className="flag-emoji">🌍</span>{taskName}</h3>;
        } else if (taskName.startsWith('himalayas-')) {
            return <h3><span className="flag-emoji">🏔️</span>{taskName}</h3>;
        }
        return <h3><span className="flag-emoji">🤖</span>{taskName}</h3>;
    };

    const getTaskDescription = (taskName) => {
        if (taskName.startsWith('adzuna-')) return `Fetches new job listings from Adzuna for the '${taskName.substring(7).toUpperCase()}' region.`;
        if (taskName === 'arbeitnow') return 'Fetches new job listings from the Arbeitnow general API.';
        if (taskName === 'jsearch-it') return 'Fetches new IT/Software job listings globally using JSearch API (LinkedIn, Indeed, etc).';
        if (taskName === 'remotive-software') return 'Fetches full description remote IT/Software jobs globally using Remotive API (No keys required).';
        if (taskName === 'himalayas-remote') return 'Fetches remote jobs globally from Himalayas App API.';
        return 'A general data import task.';
    };

    const getStatusColor = (status) => {
        switch(status) {
            case 'PROCESSING': return '#17a2b8';
            case 'SAVED': return '#28a745';
            case 'SKIPPED': return '#ffc107';
            case 'ERROR': return '#dc3545';
            case 'FINISHED': return '#6f42c1';
            default: return '#6c757d';
        }
    };

    const renderSummary = () => {
        const savedJobs = progressEvents.filter(e => e.status === 'SAVED');
        const skippedJobs = progressEvents.filter(e => e.status === 'SKIPPED');
        const errorJobs = progressEvents.filter(e => e.status === 'ERROR');

        return (
            <div className="summary-section">
                <h3>Import Summary</h3>
                <div className="summary-grid">
                    <div className="summary-box saved">
                        <h4>{savedJobs.length} Saved</h4>
                        <ul>{savedJobs.map((e, i) => <li key={i}><strong>{e.jobTitle}</strong> - {e.details}</li>)}</ul>
                    </div>
                    <div className="summary-box skipped">
                        <h4>{skippedJobs.length} Skipped</h4>
                        <ul>{skippedJobs.map((e, i) => <li key={i}><strong>{e.jobTitle}</strong> - {e.details}</li>)}</ul>
                    </div>
                    <div className="summary-box error">
                        <h4>{errorJobs.length} Errors</h4>
                        <ul>{errorJobs.map((e, i) => <li key={i}><strong>{e.jobTitle}</strong> - {e.details}</li>)}</ul>
                    </div>
                </div>
            </div>
        );
    };

    return (
        <div className="admin-container">
            <h1 className="admin-title">Task Administration</h1>
            <p className="admin-subtitle">Manually trigger backend tasks and manage database backups.</p>

            <div className="section-container">
                <h2 className="section-title">Demo Presentation Tool</h2>
                <div className="task-card" style={{ border: '2px solid #007bff' }}>
                    <div className="task-info">
                        <h3>Real-time Database Population</h3>
                        <p>Select an API source, enter keywords, and watch the live extraction of skills.</p>
                        
                        <div className="demo-controls">
                            <select value={selectedApi} onChange={(e) => setSelectedApi(e.target.value)} className="api-select">
                                {tasks.map(task => <option key={task} value={task}>{task}</option>)}
                            </select>
                            <input 
                                type="text" 
                                className="keyword-input"
                                style={{ padding: '10px', width: '100%', marginTop: '10px', borderRadius: '4px', border: '1px solid #ccc' }}
                                value={keywords}
                                onChange={(e) => setKeywords(e.target.value)}
                                placeholder='e.g., data analytics, "cyber security"'
                            />
                        </div>
                    </div>
                    <div className="task-actions" style={{ marginTop: '15px' }}>
                        <button 
                            onClick={handlePopulateByKeywords} 
                            disabled={isPopulating} 
                            className="task-button demo-button" 
                            style={{ backgroundColor: '#28a745', fontSize: '1.1em', width: '100%' }}
                        >
                            {isPopulating ? 'Process Running...' : '🚀 Start Live Demo'}
                        </button>
                    </div>
                </div>

                {(isPopulating || progressEvents.length > 0) && (
                    <div ref={progressPanelRef} className="progress-panel" style={{ marginTop: '20px', padding: '15px', backgroundColor: '#212529', borderRadius: '8px', color: '#f8f9fa', height: '400px', overflowY: 'auto', border: '1px solid #495057' }}>
                        <h4 className="progress-title" style={{ margin: '0 0 15px 0', borderBottom: '1px solid #495057', paddingBottom: '10px', position: 'sticky', top: 0, backgroundColor: '#212529', zIndex: 1 }}>
                            {showSummary ? 'Final Summary' : 'Live Ingestion Log'}
                        </h4>
                        {showSummary ? renderSummary() : (
                            <div className="log-container" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                {progressEvents.map((event, index) => (
                                    <div key={index} className="log-entry" style={{ padding: '10px', borderRadius: '4px', backgroundColor: '#343a40', borderLeft: `4px solid ${getStatusColor(event.status)}` }}>
                                        <div className="log-header" style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '5px' }}>
                                            <strong style={{ color: getStatusColor(event.status) }}>[{event.status}]</strong>
                                            <span className="log-title" style={{ fontSize: '0.9em', color: '#adb5bd' }}>{event.jobTitle}</span>
                                        </div>
                                        <div className="log-details" style={{ fontSize: '0.95em', color: event.status === 'ERROR' ? '#ff6b6b' : '#f8f9fa' }}>{event.details}</div>
                                    </div>
                                ))}
                            </div>
                        )}
                        <div style={{ marginTop: '25px', textAlign: 'center', paddingTop: '20px', borderTop: '1px dashed #ced4da' }}>
                            <p style={{ color: '#adb5bd', fontSize: '0.95em', marginBottom: '10px' }}>To see the new nodes, run this query in Memgraph Lab:</p>
                            <pre style={{ backgroundColor: '#e9ecef', padding: '10px', borderRadius: '4px', fontFamily: 'monospace', color: '#495057', display: 'block', whiteSpace: 'pre-wrap', wordWrap: 'break-word', textAlign: 'left' }}>
                                MATCH (j:Job)-[r]-(c:Company) WHERE toLower(j.title) CONTAINS '{keywords.toLowerCase().split(' ')[0]}' RETURN j,r,c LIMIT 25
                            </pre>
                            <button onClick={() => window.open('http://localhost:3000', '_blank')} style={{ marginTop: '15px', padding: '10px 25px', border: 'none', borderRadius: '5px', backgroundColor: '#fd7e14', color: 'white', fontWeight: 'bold', cursor: 'pointer' }}>
                                Open Memgraph Lab
                            </button>
                        </div>
                    </div>
                )}
            </div>

            <div className="section-container">
                <h2 className="section-title">Data Import Tasks</h2>
                <p className="section-subtitle">All import tasks run automatically once per day. You can also trigger them manually below.</p>
                {tasks.map(task => (
                    <div className="task-card" key={task}>
                        <div className="task-info">{renderTaskTitle(task)}<p>{getTaskDescription(task)}</p></div>
                        <div className="task-actions">
                            <button onClick={() => handleTriggerImport(task)} disabled={runningTasks.includes(task)} className="task-button">{runningTasks.includes(task) ? 'Running...' : 'Trigger Task'}</button>
                            {taskMessages[task] && <p className={`feedback-message ${taskMessages[task].includes('Error') ? 'error' : 'success'}`}>{taskMessages[task]}</p>}
                        </div>
                    </div>
                ))}
            </div>

            <div className="section-container">
                <h2 className="section-title">Maintenance Tasks</h2>
                <div className="task-card">
                    <div className="task-info"><h3>Weekly Database Pruning</h3><p>Archives and deletes jobs older than 21 days. Runs automatically every Sunday.</p></div>
                    <div className="task-actions">
                        <button onClick={handleTriggerPruning} disabled={isPruning} className="task-button">{isPruning ? 'Pruning...' : 'Trigger Pruning'}</button>
                        {pruningMessage && <p className={`feedback-message ${pruningMessage.includes('Error') ? 'error' : 'success'}`}>{pruningMessage}</p>}
                    </div>
                </div>

                <div className="task-card" style={{ marginTop: '20px' }}>
                     <div className="task-info">
                         <h3>Restore from Pruning Archives</h3>
                         <p>Select a ZIP archive created during a past pruning process to restore the old jobs back into the database.</p>
                     </div>
                     <div className="backup-list">
                         {pruningArchives.length > 0 ? (
                             <ul>
                                 {pruningArchives.map(file => (
                                     <li key={file}>
                                         <span>{file}</span>
                                         <button 
                                             onClick={() => handleRestoreArchive(file)} 
                                             disabled={isRestoringArchive === file} 
                                             className="load-button"
                                         >
                                             {isRestoringArchive === file ? 'Restoring...' : 'Restore Jobs'}
                                         </button>
                                     </li>
                                 ))}
                             </ul>
                         ) : (
                             <p>No pruning archives found in the 'archives' folder.</p>
                         )}
                         {archiveMessage && <p className={`feedback-message ${archiveMessage.includes('Error') ? 'error' : 'success'}`}>{archiveMessage}</p>}
                     </div>
                 </div>
            </div>

            <div className="section-container">
                <h2 className="section-title">Database Backup & Restore</h2>
                <div className="task-card">
                    <div className="task-info">
                        <h3>Automatic Backup</h3>
                        <p>A <code>.cypherl</code> script is created automatically in the 'backups' directory every time the application is shut down.</p>
                    </div>
                </div>
                <div className="task-card">
                    <div className="task-info"><h3>Available CypherL Backups</h3><p>Select a backup script to execute it against the database. <strong>Warning: This will clear the current database before loading.</strong></p></div>
                    <div className="backup-list">
                        {backups.length > 0 ? (
                            <ul>
                                {backups.map(file => (
                                    <li key={file}>
                                        <span>📜 {file}</span>
                                        <button onClick={() => handleLoadBackup(file)} disabled={isRestoring === file} className="load-button" style={{backgroundColor: '#dc3545'}}>
                                            {isRestoring === file ? 'Loading...' : 'Restore Full Backup'}
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        ) : (<p>No backup files found.</p>)}
                        {backupMessage && <p className={`feedback-message ${backupMessage.includes('Error') ? 'error' : 'success'}`}>{backupMessage}</p>}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default AdminPage;