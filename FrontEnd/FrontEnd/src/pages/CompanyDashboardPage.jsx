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

    // On component mount, get the current user and fetch/merge their jobs
    useEffect(() => {
        const currentUser = authService.getCurrentUser();
        if (currentUser && currentUser.username) {
            const companyName = currentUser.username;
            setJobForm(prev => ({ ...prev, companyName: companyName }));
            
            const fetchAndMergeJobs = async () => {
                try {
                    // 1. Fetch jobs from the database
                    let dbJobs = [];
                    // Use find-or-create to avoid 404 errors if the company is new and hasn't posted yet
                    const compRes = await fetch(`/api/companies/find-or-create`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            ...authHeader()
                        },
                        body: JSON.stringify({ name: companyName })
                    });

                    if (compRes.ok) {
                        const company = await compRes.json();
                        const jobsRes = await fetch(`/api/companies/${company.id}/jobs`, {
                            headers: authHeader()
                        });
                        if (jobsRes.ok) {
                            dbJobs = await jobsRes.json();
                        }
                    }

                    // 2. Load jobs from localStorage
                    const localJobsRaw = localStorage.getItem('companyDashboardJobs');
                    const localJobs = localJobsRaw ? JSON.parse(localJobsRaw) : [];

                    // 3. Merge the lists
                    const dbJobIds = new Set(dbJobs.map(j => j.id));
                    const uniqueLocalJobs = localJobs.filter(localJob => 
                        !dbJobIds.has(localJob.id) && localJob.companyName === companyName
                    );

                    const mergedJobs = [...dbJobs, ...uniqueLocalJobs];
                    setJobs(mergedJobs);

                } catch (err) {
                    console.error("Failed to fetch and merge jobs:", err);
                    setError("Could not load your company's jobs.");
                }
            };
            fetchAndMergeJobs();
        }
    }, []);

    // Persist jobs to localStorage whenever they change
    useEffect(() => {
        try {
            localStorage.setItem('companyDashboardJobs', JSON.stringify(jobs));
        } catch (err) {
            console.error("Failed to save jobs to localStorage", err);
        }
    }, [jobs]);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setJobForm(prev => ({ ...prev, [name]: value }));
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
                    <p>{editingJobId ? 'Update the details of your job below.' : 'Fill out the form below to post a job listing.'}</p>
                    <form onSubmit={handleAddOrUpdateJob} className="add-job-form">
                        <input
                            type="text"
                            name="companyName"
                            value={jobForm.companyName}
                            onChange={handleInputChange}
                            placeholder="Your Company Name"
                            required
                            disabled // Company name is locked to the logged-in user
                        />
                        <input
                            type="text"
                            name="title"
                            value={jobForm.title}
                            onChange={handleInputChange}
                            placeholder="Job Title"
                            required
                        />
                        <textarea
                            name="description"
                            value={jobForm.description}
                            onChange={handleInputChange}
                            placeholder="Job Description"
                            required
                        />
                        <input
                            type="text"
                            name="location"
                            value={jobForm.location}
                            onChange={handleInputChange}
                            placeholder="Location (e.g., City, Country)"
                            required
                        />
                        <input
                            type="text"
                            name="category"
                            value={jobForm.category}
                            onChange={handleInputChange}
                            placeholder="Job Category"
                            required
                        />
                        <input
                            type="text"
                            name="experienceLevel"
                            value={jobForm.experienceLevel}
                            onChange={handleInputChange}
                            placeholder="Experience Level (e.g., 2 years)"
                        />
                        <input
                            type="text"
                            name="programmingLanguages"
                            value={jobForm.programmingLanguages}
                            onChange={handleInputChange}
                            placeholder="Languages (comma separated)"
                        />
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