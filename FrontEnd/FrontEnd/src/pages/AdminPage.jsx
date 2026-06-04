import React, { useState, useEffect } from 'react';
import authHeader from '../services/auth-header';
import '../styles/AdminPage.css';

const AdminPage = () => {
    // State pentru task-urile de import
    const [tasks, setTasks] = useState([]);
    const [taskMessages, setTaskMessages] = useState({});
    const [runningTasks, setRunningTasks] = useState([]);

    // State pentru task-ul de pruning
    const [pruningMessage, setPruningMessage] = useState('');
    const [isPruning, setIsPruning] = useState(false);

    // State pentru arhivele de pruning
    const [pruningArchives, setPruningArchives] = useState([]);
    const [archiveMessage, setArchiveMessage] = useState('');
    const [isRestoringArchive, setIsRestoringArchive] = useState(null);

    // State pentru backup & restore
    const [backups, setBackups] = useState([]);
    const [backupMessage, setBackupMessage] = useState('');
    const [isRestoring, setIsRestoring] = useState(null);

    // New state for the demo keyword population
    const [keywords, setKeywords] = useState('data analytics');
    const [isPopulating, setIsPopulating] = useState(false);
    const [populateStats, setPopulateStats] = useState(null);
    const [populateError, setPopulateError] = useState('');

    useEffect(() => {
        fetchTasks();
        fetchBackups();
        fetchPruningArchives();
    }, []);

    const fetchTasks = async () => {
        try {
            const response = await fetch('/api/maintenance/tasks', { headers: authHeader() });
            if (!response.ok) throw new Error('Failed to fetch tasks');
            setTasks(await response.json());
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

    const handlePopulateByKeywords = async () => {
        if (isPopulating || !keywords.trim()) return;
        setIsPopulating(true);
        setPopulateStats(null);
        setPopulateError('');
        
        try {
            const response = await fetch('/api/maintenance/populate-by-keywords', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({ keywords: keywords.trim() }),
            });
            
            if (!response.ok) {
                throw new Error('Failed to start population task');
            }
            
            const stats = await response.json();
            setPopulateStats(stats);
            
        } catch (error) {
            setPopulateError(`Error: ${error.message}`);
        } finally {
            setIsPopulating(false);
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
        const isConfirmed = window.confirm(`CONFIRM LOAD BACKUP\n\nThis will DELETE ALL EXISTING DATA...`);
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

    return (
        <div className="admin-container">
            <h1 className="admin-title">Task Administration</h1>
            <p className="admin-subtitle">Manually trigger backend tasks and manage database backups.</p>

            <div className="section-container">
                <h2 className="section-title">Demo Presentation Tool</h2>
                <div className="task-card" style={{ border: '2px solid #007bff' }}>
                    <div className="task-info">
                        <h3>Populate Database by Keywords</h3>
                        <p>Enter keywords to fetch jobs from multiple sources and populate the database. Ideal for live demos.</p>
                        <input 
                            type="text" 
                            className="keyword-input"
                            style={{ padding: '10px', width: '100%', marginTop: '10px', borderRadius: '4px', border: '1px solid #ccc' }}
                            value={keywords}
                            onChange={(e) => setKeywords(e.target.value)}
                            placeholder='e.g., data analytics, "cyber security + IoT"'
                        />
                    </div>
                    <div className="task-actions" style={{ marginTop: '15px' }}>
                        <button 
                            onClick={handlePopulateByKeywords} 
                            disabled={isPopulating} 
                            className="task-button demo-button" 
                            style={{ backgroundColor: '#28a745', fontSize: '1.1em', width: '100%' }}
                        >
                            {isPopulating ? (
                                <span>⏳ Fetching jobs (this takes ~5 seconds)...</span>
                            ) : (
                                '🚀 Populate for Demo'
                            )}
                        </button>
                        
                        {populateError && <p className="error-message" style={{ color: '#dc3545', marginTop: '10px' }}>{populateError}</p>}
                        
                        {populateStats && (
                            <div className="demo-results-container">
                                <h4 className="results-title">Import Results</h4>
                                <div className="stats-grid">
                                    <div>
                                        <div className="stat-number" style={{ color: '#17a2b8' }}>{populateStats.totalFound}</div>
                                        <div className="stat-label">Jobs Found</div>
                                    </div>
                                    <div>
                                        <div className="stat-number" style={{ color: '#28a745' }}>{populateStats.saved}</div>
                                        <div className="stat-label">Successfully Saved</div>
                                    </div>
                                    <div>
                                        <div className="stat-number" style={{ color: populateStats.errors > 0 ? '#dc3545' : '#6c757d' }}>{populateStats.errors}</div>
                                        <div className="stat-label">Errors / Skipped</div>
                                    </div>
                                </div>
                                <div className="memgraph-link-container">
                                    <p>To see the new nodes, run this query in Memgraph Lab:</p>
                                    <pre className="cypher-query">MATCH (j:Job)-[r]-(c:Company) WHERE toLower(j.title) CONTAINS '{keywords.toLowerCase().split(' ')[0]}' RETURN j,r,c LIMIT 25</pre>
                                    <button onClick={() => window.open('http://localhost:3000', '_blank')} className="memgraph-button">
                                        Open Memgraph Lab
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
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