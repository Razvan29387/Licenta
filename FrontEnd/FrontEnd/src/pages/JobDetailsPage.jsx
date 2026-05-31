import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import authHeader from '../services/auth-header';
import '../styles/JobDetailsPage.css';

const JobDetailsPage = () => {
    const { id } = useParams();
    const [job, setJob] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        const fetchJob = async () => {
            try {
                // Modified to include auth headers
                const response = await fetch(`/api/jobs/${id}`, {
                    headers: authHeader()
                });
                if (!response.ok) {
                    throw new Error('Job not found');
                }
                const data = await response.json();
                setJob(data);
            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };

        fetchJob();
    }, [id]);

    if (loading) return <div className="loading-state">Loading job details...</div>;
    if (error) return <div className="error-state">Error: {error}</div>;
    if (!job) return <div className="error-state">Job not found.</div>;

    return (
        <div className="job-details-container">
            <Link to="/jobs" className="back-link">&larr; Back to all jobs</Link>
            
            <div className="job-details-card">
                <div className="job-details-header">
                    <h1>{job.title}</h1>
                    <div className="company-info">
                        <span className="company-icon">🏢</span>
                        <span className="company-name">{job.companyName || 'Unknown Company'}</span>
                    </div>
                </div>

                <div className="job-meta-grid">
                    <div className="meta-item">
                        <span className="meta-label">Location</span>
                        <span className="meta-value">{job.location || job.country || 'Not specified'}</span>
                    </div>
                    <div className="meta-item">
                        <span className="meta-label">Category</span>
                        <span className="meta-value">{job.category || 'Not specified'}</span>
                    </div>
                    <div className="meta-item">
                        <span className="meta-label">Experience</span>
                        <span className="meta-value">{job.experienceLevel || 'Not specified'}</span>
                    </div>
                    <div className="meta-item">
                        <span className="meta-label">Contract Type</span>
                        <span className="meta-value">{job.contractType || 'Not specified'}</span>
                    </div>
                </div>

                <div className="job-description-section">
                    <h2>Job Description</h2>
                    <div 
                        className="description-content"
                        dangerouslySetInnerHTML={{ __html: job.description }} 
                    />
                </div>

                {job.programmingLanguages && job.programmingLanguages.length > 0 && (
                    <div className="tags-section">
                        <h3>Required Skills</h3>
                        <div className="tags-container">
                            {job.programmingLanguages.map((lang, index) => (
                                <span key={index} className="tag">{lang}</span>
                            ))}
                        </div>
                    </div>
                )}

                <div className="apply-section">
                    {job.url ? (
                        <a href={job.url} target="_blank" rel="noopener noreferrer" className="apply-button">
                            Apply on Company Website
                        </a>
                    ) : (
                        <button className="apply-button" onClick={() => alert('Internal application process coming soon!')}>
                            Apply Now
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default JobDetailsPage;