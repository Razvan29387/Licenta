import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import authHeader from '../services/auth-header';
import '../styles/JobDetailsPage.css';

const JobDetailsPage = () => {
    const { id } = useParams();
    const [job, setJob] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // State for the application modal
    const [showApplyModal, setShowApplyModal] = useState(false);
    const [applicantName, setApplicantName] = useState('');
    const [candidateCv, setCandidateCv] = useState('');
    const [applicationMessage, setApplicationMessage] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    useEffect(() => {
        const fetchJob = async () => {
            try {
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

    const handleApplySubmit = async (e) => {
        e.preventDefault();
        if (!applicantName.trim() || !candidateCv.trim()) {
            setApplicationMessage('Please fill in both your name and CV.');
            return;
        }
        setIsSubmitting(true);
        setApplicationMessage('');

        try {
            const response = await fetch(`/api/applications/job/${id}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...authHeader()
                },
                body: JSON.stringify({ applicantName, candidateCv })
            });

            const responseText = await response.text();
            if (!response.ok) {
                throw new Error(responseText || 'Failed to submit application.');
            }

            setApplicationMessage('Application submitted successfully!');
            setTimeout(() => {
                setShowApplyModal(false);
                setApplicationMessage('');
            }, 2000);

        } catch (err) {
            setApplicationMessage(`Error: ${err.message}`);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (loading) return <div className="loading-state">Loading job details...</div>;
    if (error) return <div className="error-state">Error: {error}</div>;
    if (!job) return <div className="error-state">Job not found.</div>;

    return (
        <div className="job-details-container">
            <Link to="/jobs" className="back-link">&larr; Back to all jobs</Link>

            <div className="job-details-card">
                {/* Job Details Header */}
                <div className="job-details-header">
                    <h1>{job.title}</h1>
                    <div className="company-info">
                        <span className="company-icon">🏢</span>
                        <span className="company-name">{job.companyName || 'Unknown Company'}</span>
                    </div>
                </div>

                {/* Job Meta Grid */}
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

                {/* Job Description */}
                <div className="job-description-section">
                    <h2>Job Description</h2>
                    <div
                        className="description-content"
                        dangerouslySetInnerHTML={{ __html: job.description }}
                    />
                </div>

                {/* Skills/Tags */}
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

                {/* Apply Button */}
                <div className="apply-section">
                    {job.url ? (
                        <a href={job.url} target="_blank" rel="noopener noreferrer" className="apply-button">
                            Apply on Company Website
                        </a>
                    ) : (
                        <button className="apply-button" onClick={() => setShowApplyModal(true)}>
                            Apply Now
                        </button>
                    )}
                </div>
            </div>

            {/* Application Modal */}
            {showApplyModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <button className="modal-close" onClick={() => setShowApplyModal(false)}>&times;</button>
                        <h2>Apply for {job.title}</h2>
                        <form onSubmit={handleApplySubmit}>
                            <div className="form-group">
                                <label htmlFor="applicantName">Your Name</label>
                                <input
                                    type="text"
                                    id="applicantName"
                                    value={applicantName}
                                    onChange={(e) => setApplicantName(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="candidateCv">Your CV (paste text)</label>
                                <textarea
                                    id="candidateCv"
                                    rows="10"
                                    value={candidateCv}
                                    onChange={(e) => setCandidateCv(e.target.value)}
                                    placeholder="Paste your full CV text here..."
                                    required
                                />
                            </div>
                            <button type="submit" className="submit-application-btn" disabled={isSubmitting}>
                                {isSubmitting ? 'Submitting...' : 'Submit Application'}
                            </button>
                            {applicationMessage && <p className="application-feedback">{applicationMessage}</p>}
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default JobDetailsPage;