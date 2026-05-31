import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import '../styles/CompanyProfilePage.css';

const CompanyProfilePage = () => {
    const { companyId } = useParams();
    const [company, setCompany] = useState(null);
    const [jobs, setJobs] = useState([]);
    const [applicants, setApplicants] = useState({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // State for the new job form
    const [newJob, setNewJob] = useState({
        title: '',
        description: '',
        location: '',
        category: 'IT', // Default category
    });

    useEffect(() => {
        const fetchData = async () => {
            try {
                setLoading(true);
                // Fetch company details, jobs, and applicants in parallel
                const [companyRes, jobsRes] = await Promise.all([
                    fetch(`/api/companies/${companyId}`),
                    fetch(`/api/companies/${companyId}/jobs`)
                ]);

                if (!companyRes.ok || !jobsRes.ok) {
                    throw new Error('Failed to fetch company data.');
                }

                const companyData = await companyRes.json();
                const jobsData = await jobsRes.json();

                setCompany(companyData);
                setJobs(jobsData);

                // Fetch applicants for each job
                const applicantsData = {};
                for (const job of jobsData) {
                    const applicantsRes = await fetch(`/api/jobs/${job.id}/applications`);
                    if (applicantsRes.ok) {
                        applicantsData[job.id] = await applicantsRes.json();
                    } else {
                        applicantsData[job.id] = [];
                    }
                }
                setApplicants(applicantsData);

            } catch (err) {
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [companyId]);

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setNewJob(prev => ({ ...prev, [name]: value }));
    };

    const handleAddJob = async (e) => {
        e.preventDefault();
        try {
            const response = await fetch(`/api/jobs`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    ...newJob,
                    companyName: company.name // Assuming company object has a name property
                })
            });

            if (response.ok) {
                const addedJob = await response.json();
                setJobs(prev => [...prev, addedJob]);
                setNewJob({ title: '', description: '', location: '', category: 'IT' }); // Reset form
            } else {
                throw new Error('Failed to add job.');
            }
        } catch (err) {
            setError(err.message);
        }
    };

    if (loading) return <div>Loading...</div>;
    if (error) return <div>Error: {error}</div>;
    if (!company) return <div>Company not found.</div>;

    return (
        <div className="company-profile-container">
            <header className="company-header">
                <h1>{company.name}</h1>
                <p>{company.description || 'No description available.'}</p>
            </header>

            <main className="company-main-content">
                <section className="section-container">
                    <h2>Add a New Job</h2>
                    <form onSubmit={handleAddJob} className="add-job-form">
                        <input
                            type="text"
                            name="title"
                            value={newJob.title}
                            onChange={handleInputChange}
                            placeholder="Job Title"
                            required
                        />
                        <textarea
                            name="description"
                            value={newJob.description}
                            onChange={handleInputChange}
                            placeholder="Job Description"
                            required
                        />
                        <input
                            type="text"
                            name="location"
                            value={newJob.location}
                            onChange={handleInputChange}
                            placeholder="Location (e.g., City, Country)"
                            required
                        />
                        <button type="submit">Post Job</button>
                    </form>
                </section>

                <section className="section-container">
                    <h2>Our Job Postings</h2>
                    <div className="job-list">
                        {jobs.length > 0 ? jobs.map(job => (
                            <div key={job.id} className="job-card">
                                <h3>{job.title}</h3>
                                <p>{job.location}</p>
                                <div className="applicants-section">
                                    <h4>Applicants</h4>
                                    {applicants[job.id] && applicants[job.id].length > 0 ? (
                                        <ul>
                                            {applicants[job.id].map(applicant => (
                                                <li key={applicant.id}>
                                                    {applicant.name} - {applicant.email}
                                                </li>
                                            ))}
                                        </ul>
                                    ) : (
                                        <p>No applicants yet.</p>
                                    )}
                                </div>
                            </div>
                        )) : (
                            <p>No jobs posted yet.</p>
                        )}
                    </div>
                </section>
            </main>
        </div>
    );
};

export default CompanyProfilePage;
