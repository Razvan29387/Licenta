import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import authHeader from '../services/auth-header';
import authService from '../services/auth.service';
import '../styles/CompanyDashboardPage.css';

const CompanyDashboardPage = () => {
    const [jobs, setJobs] = useState([]);
    const [error, setError] = useState(null);
    const [editingJobId, setEditingJobId] = useState(null);
    const [applicantsData, setApplicantsData] = useState({});
    const [viewingApplicantsFor, setViewingApplicantsFor] = useState(null);

    const [jobForm, setJobForm] = useState({
        title: '',
        description: '',
        location: '',
        category: 'IT',
        companyName: '',
        experienceLevel: '',
        programmingLanguages: '',
    });
    
    const [rawNotes, setRawNotes] = useState('');
    const [isOptimizing, setIsOptimizing] = useState(false);

    useEffect(() => {
        const currentUser = authService.getCurrentUser();
        if (currentUser && currentUser.username) {
            const companyName = currentUser.username;
            setJobForm(prev => ({ ...prev, companyName: companyName }));
            
            const fetchJobs = async () => {
                try {
                    const compRes = await fetch(`/api/companies/find-or-create`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', ...authHeader() },
                        body: JSON.stringify({ name: companyName })
                    });
                    
                    if (compRes.ok) {
                        const company = await compRes.json();
                        if (company && company.id) {
                            const jobsRes = await fetch(`/api/companies/${company.id}/jobs`, { headers: authHeader() });
                            if (jobsRes.ok) setJobs(await jobsRes.json());
                        }
                    }
                } catch (err) {
                    setError("Could not load your company's jobs. " + err.message);
                }
            };
            fetchJobs();
        }
    }, []);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setJobForm(prev => ({ ...prev, [name]: value }));
    };

    const handleGenerateDescription = async () => {
        if (!rawNotes.trim() || !jobForm.title.trim()) {
            alert('Please provide a Job Title and some keywords/notes first.');
            return;
        }
        setIsOptimizing(true);
        setError(null);
        try {
            const response = await fetch('/api/ai/optimize-description', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', ...authHeader() },
                body: JSON.stringify({
                    title: jobForm.title,
                    rawNotes: rawNotes,
                    category: jobForm.category
                })
            });
            if (!response.ok) throw new Error('Failed to generate description.');
            
            const generatedDescription = await response.text();
            setJobForm(prev => ({ ...prev, description: generatedDescription }));

        } catch (err) {
            setError(err.message);
        } finally {
            setIsOptimizing(false);
        }
    };

    const handleAddOrUpdateJob = async (e) => {
        e.preventDefault();
        setError(null);

        if (!jobForm.companyName.trim()) {
            setError('Company name is required.');
            return;
        }

        const method = editingJobId ? 'PUT' : 'POST';
        const url = editingJobId ? `/api/jobs/${editingJobId}` : '/api/jobs';

        try {
            const response = await fetch(url, {
                method: method,
                headers: { 
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({
                    ...jobForm,
                    programmingLanguages: typeof jobForm.programmingLanguages === 'string' 
                        ? jobForm.programmingLanguages.split(',').map(lang => lang.trim()).filter(lang => lang)
                        : jobForm.programmingLanguages,
                })
            });

            if (response.ok) {
                const savedJob = await response.json();
                
                if (editingJobId) {
                    setJobs(prev => prev.map(job => job.id === editingJobId ? savedJob : job));
                    setEditingJobId(null);
                } else {
                    setJobs(prev => [savedJob, ...prev.filter(j => j.id !== savedJob.id)]);
                }

                setJobForm(prev => ({ 
                    ...prev,
                    title: '', 
                    description: '', 
                    location: '', 
                    category: 'IT',
                    experienceLevel: '',
                    programmingLanguages: '',
                }));
                setRawNotes('');
            } else {
                const errorData = await response.text();
                throw new Error(`Failed to save job: ${errorData}`);
            }
        } catch (err) {
            setError(err.message);
        }
    };

    const handleEditClick = (e, job) => {
        e.preventDefault();
        e.stopPropagation();
        
        setEditingJobId(job.id);
        setJobForm({
            title: job.title || '',
            description: job.description || '',
            location: job.location || '',
            category: job.category || 'IT',
            companyName: job.companyName || '',
            experienceLevel: job.experienceLevel || '',
            programmingLanguages: job.programmingLanguages ? job.programmingLanguages.join(', ') : '',
        });
        setRawNotes('');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    const handleCancelEdit = () => {
        setEditingJobId(null);
        setJobForm(prev => ({ 
            ...prev,
            title: '', 
            description: '', 
            location: '', 
            category: 'IT',
            experienceLevel: '',
            programmingLanguages: '',
        }));
        setRawNotes('');
    };

    const handleViewApplicants = async (e, jobId) => {
        e.preventDefault();
        e.stopPropagation();
        
        if (viewingApplicantsFor === jobId) {
            setViewingApplicantsFor(null);
            return;
        }

        try {
            const response = await fetch(`/api/jobs/${jobId}/applications`, {
                headers: authHeader()
            });
            if (response.ok) {
                const data = await response.json();
                setApplicantsData(prev => ({ ...prev, [jobId]: data }));
                setViewingApplicantsFor(jobId);
            } else {
                throw new Error('Failed to load applicants.');
            }
        } catch (err) {
            alert(err.message);
        }
    };

    return (
        <div className="company-dashboard-container">
            <main className="company-dashboard-main">
                <section className="section-container add-job-section">
                    <h2>{editingJobId ? 'Edit Job' : 'Post a New Job'}</h2>
                    <form onSubmit={handleAddOrUpdateJob} className="add-job-form">
                        <input type="text" name="companyName" value={jobForm.companyName} onChange={handleInputChange} placeholder="Your Company Name" required disabled />
                        <input type="text" name="title" value={jobForm.title} onChange={handleInputChange} placeholder="Job Title" required />
                        
                        <div className="description-optimizer-container">
                            <textarea
                                name="rawNotes"
                                value={rawNotes}
                                onChange={(e) => setRawNotes(e.target.value)}
                                placeholder="Enter keywords to generate a description (e.g., 'Java, Spring, 5+ years, remote')"
                                rows="3"
                            />
                            <button type="button" onClick={handleGenerateDescription} disabled={isOptimizing} className="ai-button">
                                {isOptimizing ? 'Generating...' : '✨ Generate with AI'}
                            </button>
                        </div>

                        <textarea
                            name="description"
                            value={jobForm.description}
                            onChange={handleInputChange}
                            placeholder="Job Description (will be generated by AI)"
                            required
                            rows="10"
                        />
                        <input type="text" name="location" value={jobForm.location} onChange={handleInputChange} placeholder="Location (e.g., City, Country)" required />
                        <input type="text" name="category" value={jobForm.category} onChange={handleInputChange} placeholder="Job Category" required />
                        <input type="text" name="experienceLevel" value={jobForm.experienceLevel} onChange={handleInputChange} placeholder="Experience Level (e.g., 2 years)" />
                        <input type="text" name="programmingLanguages" value={jobForm.programmingLanguages} onChange={handleInputChange} placeholder="Languages (comma separated)" />
                        
                        <div className="form-actions">
                            <button type="submit">{editingJobId ? 'Save Changes' : 'Post Job'}</button>
                            {editingJobId && (
                                <button type="button" className="cancel-btn" onClick={handleCancelEdit}>Cancel</button>
                            )}
                        </div>
                        {error && <p className="error-message">{error}</p>}
                    </form>
                </section>

                <section className="section-container posted-jobs-section">
                    <h2>Your Jobs</h2>
                    <div className="job-list">
                        {jobs.length > 0 ? jobs.map(job => (
                            <div key={job.id || job.adzunaId} className="job-card-wrapper">
                                <Link to={`/jobs/${job.id}`} className="job-card-link">
                                    <div className="job-card">
                                        <div className="job-card-header">
                                            <h3>{job.title}</h3>
                                            <div className="job-actions">
                                                <button onClick={(e) => handleEditClick(e, job)} className="edit-btn">Edit</button>
                                                <button onClick={(e) => handleViewApplicants(e, job.id)} className="view-applicants-btn">
                                                    {viewingApplicantsFor === job.id ? 'Hide Applicants' : 'View Applicants'}
                                                </button>
                                            </div>
                                        </div>
                                        <p className="job-company">{job.companyName}</p>
                                        <p className="job-location">{job.location}</p>

                                        {viewingApplicantsFor === job.id && (
                                            <div className="applicants-container" onClick={(e) => e.preventDefault()}>
                                                <h4>Applicants ({applicantsData[job.id]?.length || 0})</h4>
                                                {applicantsData[job.id] && applicantsData[job.id].length > 0 ? (
                                                    <ul className="applicant-list">
                                                        {applicantsData[job.id].map(app => (
                                                            <li key={app.id} className="applicant-item">
                                                                <div className="applicant-info">
                                                                    <strong>{app.applicantName}</strong>
                                                                    {app.aiScore !== null && (
                                                                        <span className="ai-score">AI Score: {app.aiScore}/100</span>
                                                                    )}
                                                                </div>
                                                                <div className="applicant-cv">
                                                                    <p><strong>CV:</strong></p>
                                                                    <pre>{app.candidateCv}</pre>
                                                                </div>
                                                                {app.aiFeedback && (
                                                                    <div className="applicant-feedback">
                                                                        <p><strong>AI Feedback:</strong></p>
                                                                        <p>{app.aiFeedback}</p>
                                                                    </div>
                                                                )}
                                                            </li>
                                                        ))}
                                                    </ul>
                                                ) : (
                                                    <p>No applications yet.</p>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </Link>
                            </div>
                        )) : (
                            <p>No jobs have been posted for this company yet.</p>
                        )}
                    </div>
                </section>
            </main>
        </div>
    );
};

export default CompanyDashboardPage;