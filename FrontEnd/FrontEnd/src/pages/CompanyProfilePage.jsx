import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';

const CompanyProfilePage = () => {
  const myCompanyName = "TechCorp Solutions"; 
  
  const [myJobs, setMyJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showForm, setShowForm] = useState(false);

  // AI Loading State
  const [isAiLoading, setIsAiLoading] = useState(false);

  // Applications View State
  const [viewingApplicationsFor, setViewingApplicationsFor] = useState(null); // Job ID
  const [applications, setApplications] = useState([]);
  const [loadingApps, setLoadingApps] = useState(false);

  const [formData, setFormData] = useState({
    title: '',
    location: '',
    country: '',
    category: '',
    description: '',
    experienceLevel: '',
    programmingLanguages: ''
  });

  const fetchMyJobs = async () => {
    try {
      setLoading(true);
      const response = await fetch(`/api/jobs?companyName=${encodeURIComponent(myCompanyName)}`);
      if (!response.ok) throw new Error("Failed to fetch company jobs");
      const data = await response.json();
      setMyJobs(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMyJobs();
  }, [myCompanyName]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleOptimizeAI = async () => {
      if (!formData.title || !formData.description) {
          alert("Please enter a Title and some basic notes in the Description field first.");
          return;
      }

      setIsAiLoading(true);
      try {
          const response = await fetch('/api/ai/optimize-description', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                  title: formData.title,
                  category: formData.category,
                  rawNotes: formData.description
              })
          });

          if (!response.ok) throw new Error("AI Optimization failed");
          
          const data = await response.json();
          setFormData(prev => ({ ...prev, description: data.optimizedDescription }));
      } catch (err) {
          alert("Error: " + err.message);
      } finally {
          setIsAiLoading(false);
      }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if(!formData.title || !formData.description) {
        alert("Title and Description are required!");
        return;
    }

    let langsArray = [];
    if (formData.programmingLanguages) {
        langsArray = formData.programmingLanguages.split(',').map(lang => lang.trim()).filter(lang => lang !== '');
    }

    try {
      const response = await fetch('/api/jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...formData,
          programmingLanguages: langsArray,
          companyName: myCompanyName
        }),
      });

      if (!response.ok) throw new Error("Failed to post job");
      
      setFormData({ title: '', location: '', country: '', category: '', description: '', experienceLevel: '', programmingLanguages: '' });
      setShowForm(false);
      fetchMyJobs();
      alert("Job posted successfully!");

    } catch (err) {
      alert("Error posting job: " + err.message);
    }
  };

  // --- FETCH APPLICATIONS FOR A JOB ---
  const handleViewApplications = async (jobId) => {
      // Toggle logic
      if (viewingApplicationsFor === jobId) {
          setViewingApplicationsFor(null);
          return;
      }

      setViewingApplicationsFor(jobId);
      setLoadingApps(true);
      setApplications([]);

      try {
          const response = await fetch(`/api/applications/job/${jobId}`);
          if (!response.ok) throw new Error("Failed to fetch applications");
          const data = await response.json();
          setApplications(data);
      } catch(err) {
          alert("Could not load applications: " + err.message);
      } finally {
          setLoadingApps(false);
      }
  };

  const styles = {
    container: { padding: '40px', maxWidth: '900px', margin: '0 auto', fontFamily: 'sans-serif' },
    headerBox: { backgroundColor: '#007bff', color: 'white', padding: '30px', borderRadius: '10px', marginBottom: '30px' },
    card: { border: '1px solid #ddd', padding: '20px', borderRadius: '8px', marginBottom: '15px', backgroundColor: 'white' },
    title: { margin: '0 0 10px 0', color: '#333' },
    tag: { backgroundColor: '#e9ecef', padding: '5px 10px', borderRadius: '15px', fontSize: '12px', marginRight: '10px', color: '#555', display: 'inline-block', marginBottom: '5px' },
    modalOverlay: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 },
    modalContent: { backgroundColor: 'white', padding: '30px', borderRadius: '10px', width: '700px', maxWidth: '90%', maxHeight: '90vh', overflowY: 'auto' },
    inputGroup: { marginBottom: '15px' },
    label: { display: 'block', marginBottom: '5px', fontWeight: 'bold', color: '#333' },
    input: { width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box', color: '#333' },
    textarea: { width: '100%', padding: '15px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box', minHeight: '200px', color: '#333', fontFamily: 'inherit', lineHeight: '1.5' },
    select: { width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box', color: '#333' },
    aiButton: { padding: '8px 15px', backgroundColor: '#6f42c1', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '5px', fontSize: '14px', marginTop: '10px' },
    
    // Application Styles
    appContainer: { marginTop: '15px', padding: '20px', backgroundColor: '#f8f9fa', borderRadius: '8px', border: '1px solid #dee2e6' },
    appCard: { backgroundColor: 'white', padding: '15px', borderRadius: '8px', marginBottom: '10px', border: '1px solid #e9ecef', display: 'flex', gap: '20px' },
    appScoreBox: { display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', width: '80px', flexShrink: 0 },
    scoreCircle: { width: '50px', height: '50px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '18px', color: 'white' },
    appContent: { flex: 1 },
    btnGroup: { display: 'flex', gap: '15px', marginTop: '10px' },
    actionBtn: { padding: '8px 15px', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold', fontSize: '13px' }
  };

  const isItJob = formData.category.toLowerCase().includes('it') || formData.category.toLowerCase().includes('software') || formData.category.toLowerCase().includes('tech') || formData.title.toLowerCase().includes('developer') || formData.title.toLowerCase().includes('engineer');

  // Helper to color-code the AI Score
  const getScoreColor = (score) => {
      if (score >= 80) return '#28a745'; // Green
      if (score >= 60) return '#fd7e14'; // Orange
      return '#dc3545'; // Red
  };

  return (
    <div style={styles.container}>
      <div style={styles.headerBox}>
        <h1 style={{margin: 0}}>Company Dashboard</h1>
        <p style={{margin: '10px 0 0 0'}}>Welcome, Representative of <strong>{myCompanyName}</strong></p>
      </div>

      <div style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px'}}>
        <h2 style={{color: '#333'}}>Jobs posted by your company</h2>
        <button onClick={() => setShowForm(true)} style={{padding: '10px 20px', backgroundColor: '#28a745', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold'}}>+ Post a New Job</button>
      </div>

      {loading && <p>Loading your jobs...</p>}
      {error && <p style={{color: 'red'}}>{error}</p>}
      
      {!loading && !error && myJobs.length === 0 && (
          <p style={{textAlign: 'center', color: '#777', padding: '40px', border: '1px dashed #ccc', borderRadius: '8px'}}>You haven't posted any jobs yet.</p>
      )}

      {myJobs.map(job => (
        <div key={job.id} style={styles.card}>
          <div style={{display: 'flex', justifyContent: 'space-between'}}>
              <div>
                  <h3 style={styles.title}>{job.title}</h3>
                  <div style={{marginBottom: '10px'}}>
                    <span style={styles.tag}>📍 {job.location || 'Remote'}</span>
                    <span style={styles.tag}>📁 {job.category || 'General'}</span>
                  </div>
              </div>
          </div>
          
          <div style={styles.btnGroup}>
              <Link to={`/jobs/${job.id}`} style={{color: '#007bff', textDecoration: 'none', fontWeight: 'bold', padding: '5px 0'}}>
                View Job Page ↗
              </Link>
              
              {/* Only show "View Applications" if it's an internal job (created on the platform) */}
              {!job.url && (
                  <button 
                    onClick={() => handleViewApplications(job.id)}
                    style={{...styles.actionBtn, backgroundColor: viewingApplicationsFor === job.id ? '#6c757d' : '#e2e3e5', color: viewingApplicationsFor === job.id ? 'white' : '#333'}}
                  >
                    {viewingApplicationsFor === job.id ? 'Hide Applications' : '👥 View Applications'}
                  </button>
              )}
          </div>

          {/* --- APPLICATIONS VIEWER SECTION --- */}
          {viewingApplicationsFor === job.id && (
              <div style={styles.appContainer}>
                  <h4 style={{marginTop: 0, color: '#495057'}}>Candidates (Ranked by AI)</h4>
                  
                  {loadingApps && <p style={{fontSize: '14px', color: '#666'}}>Loading candidates...</p>}
                  
                  {!loadingApps && applications.length === 0 && (
                      <p style={{fontSize: '14px', color: '#666'}}>No applications received yet for this job.</p>
                  )}

                  {!loadingApps && applications.map(app => (
                      <div key={app.id} style={styles.appCard}>
                          <div style={styles.appScoreBox}>
                              <div style={{...styles.scoreCircle, backgroundColor: getScoreColor(app.aiScore)}}>
                                  {app.aiScore}
                              </div>
                              <span style={{fontSize: '11px', color: '#666', marginTop: '5px'}}>AI Score</span>
                          </div>
                          
                          <div style={styles.appContent}>
                              <h4 style={{margin: '0 0 5px 0', color: '#333'}}>{app.applicantName}</h4>
                              <p style={{margin: '0 0 10px 0', fontSize: '13px', color: '#555', backgroundColor: '#fdfdfe', padding: '10px', borderRadius: '5px', borderLeft: `3px solid ${getScoreColor(app.aiScore)}`}}>
                                  <strong>AI Feedback:</strong> {app.aiFeedback}
                              </p>
                              
                              <details>
                                  <summary style={{cursor: 'pointer', fontSize: '13px', color: '#007bff', fontWeight: 'bold'}}>View Full CV</summary>
                                  <p style={{fontSize: '13px', color: '#444', whiteSpace: 'pre-wrap', marginTop: '10px', padding: '10px', backgroundColor: '#f1f3f5', borderRadius: '5px'}}>
                                      {app.candidateCv}
                                  </p>
                              </details>
                          </div>
                      </div>
                  ))}
              </div>
          )}
        </div>
      ))}

      {/* Formular Modal - ramas la fel */}
      {showForm && (
        <div style={styles.modalOverlay}>
          <div style={styles.modalContent}>
            <h2 style={{marginTop: 0, color: '#333'}}>Post a New Job</h2>
            <form onSubmit={handleSubmit}>
              <div style={styles.inputGroup}>
                <label style={styles.label}>Job Title *</label>
                <input style={styles.input} type="text" name="title" value={formData.title} onChange={handleChange} required placeholder="e.g. Senior Java Developer" />
              </div>
              
              <div style={{display: 'flex', gap: '15px', marginBottom: '15px'}}>
                  <div style={{flex: 1}}><label style={styles.label}>City</label><input style={styles.input} type="text" name="location" value={formData.location} onChange={handleChange} /></div>
                  <div style={{flex: 1}}><label style={styles.label}>Country</label><input style={styles.input} type="text" name="country" value={formData.country} onChange={handleChange} /></div>
              </div>

              <div style={styles.inputGroup}>
                <label style={styles.label}>Category</label>
                <input style={styles.input} type="text" name="category" value={formData.category} onChange={handleChange} placeholder="e.g. IT, Software" />
              </div>

              {isItJob && (
                  <div style={{backgroundColor: '#f8f9fa', padding: '15px', borderRadius: '8px', marginBottom: '15px', border: '1px solid #e9ecef'}}>
                      <h4 style={{marginTop: 0, marginBottom: '10px', color: '#495057'}}>💻 Technical Requirements</h4>
                      <div style={styles.inputGroup}>
                        <label style={styles.label}>Experience Level</label>
                        <select style={styles.select} name="experienceLevel" value={formData.experienceLevel} onChange={handleChange}>
                            <option value="">Select level...</option><option value="Internship">Internship</option><option value="Junior">Junior</option><option value="Mid-Level">Mid-Level</option><option value="Senior">Senior</option>
                        </select>
                      </div>
                      <div style={styles.inputGroup}>
                        <label style={styles.label}>Programming Languages (comma separated)</label>
                        <input style={styles.input} type="text" name="programmingLanguages" value={formData.programmingLanguages} onChange={handleChange} />
                      </div>
                  </div>
              )}

              <div style={styles.inputGroup}>
                <label style={styles.label}>Job Description *</label>
                <p style={{fontSize: '12px', color: '#666', marginBottom: '5px', marginTop: 0}}>Write some raw notes and click the AI button to generate a professional description.</p>
                <textarea 
                    style={styles.textarea} 
                    name="description" 
                    value={formData.description} 
                    onChange={handleChange} 
                    required 
                    placeholder="e.g. Need a java dev with 3 years exp. Must know spring boot. Good salary, remote." 
                />
                
                {/* AI BUTTON */}
                <button type="button" onClick={handleOptimizeAI} disabled={isAiLoading} style={{...styles.aiButton, opacity: isAiLoading ? 0.7 : 1}}>
                  {isAiLoading ? '✨ Optimizing with AI...' : '✨ Optimize Description with AI'}
                </button>
              </div>

              <div style={{display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '30px', borderTop: '1px solid #eee', paddingTop: '20px'}}>
                <button type="button" onClick={() => setShowForm(false)} style={{padding: '10px 15px', backgroundColor: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer'}}>Cancel</button>
                <button type="submit" style={{padding: '10px 20px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold'}}>Publish Job</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CompanyProfilePage;