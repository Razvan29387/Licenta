import React, { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';

const JobDetailsPage = () => {
  const { id } = useParams();
  const [job, setJob] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Application State
  const [showApplyForm, setShowApplyForm] = useState(false);
  const [applicantName, setApplicantName] = useState('');
  const [cvText, setCvText] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [applySuccess, setApplySuccess] = useState(false);

  useEffect(() => {
    const fetchJobDetails = async () => {
      try {
        const response = await fetch(`/api/jobs/${id}`);
        if (!response.ok) {
            if (response.status === 404) throw new Error("Job not found");
            throw new Error(`Server error: ${response.status}`);
        }
        const data = await response.json();
        setJob(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    fetchJobDetails();
  }, [id]);

  const handleSubmitApplication = async (e) => {
      e.preventDefault();
      if (!applicantName || !cvText || cvText.trim().length < 50) {
          alert("Please provide your name and a meaningful CV/experience summary.");
          return;
      }

      setIsSubmitting(true);

      try {
          const response = await fetch(`/api/applications/job/${id}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ 
                  applicantName: applicantName,
                  candidateCv: cvText 
              })
          });

          if (!response.ok) throw new Error("Failed to submit application");
          
          setApplySuccess(true);
          setShowApplyForm(false);
      } catch (err) {
          alert("Error: " + err.message);
      } finally {
          setIsSubmitting(false);
      }
  };

  if (loading) return <div style={{textAlign: 'center', marginTop: '50px', color: '#333'}}>Loading job details...</div>;
  if (error) return <div style={{textAlign: 'center', marginTop: '50px', color: 'red'}}>{error}</div>;
  if (!job) return null;

  const companyName = typeof job.company === 'string' ? job.company : (job.company?.name || job.company?.display_name || "Unknown Company");
  const categoryName = typeof job.category === 'string' ? job.category : (job.category?.label || "Uncategorized");
  const locationName = typeof job.location === 'string' ? job.location : (job.location?.display_name || "Unknown Location");
  
  const formatSalary = (min, max, period) => {
      if (!min && !max) return null;
      const formatNumber = (num) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(num);
      let salaryStr = (min && max && min !== max) ? `${formatNumber(min)} - ${formatNumber(max)}` : formatNumber(min || max);
      return period ? `${salaryStr} / ${period}` : salaryStr;
  };
  
  const formattedSalary = formatSalary(job.salaryMin, job.salaryMax, job.salaryPeriod);
  
  // Verificăm dacă e un job intern (creat de firma ta) sau extern (agregat)
  const isInternalJob = !job.url; 

  const styles = {
    container: { padding: '40px', maxWidth: '800px', margin: '0 auto', fontFamily: 'sans-serif' },
    backLink: { display: 'inline-block', marginBottom: '20px', color: '#007bff', textDecoration: 'none', fontWeight: 'bold' },
    headerBox: { borderBottom: '2px solid #eaeaea', paddingBottom: '20px', marginBottom: '20px' },
    title: { fontSize: '28px', color: '#333', margin: '0 0 10px 0' },
    companyLine: { fontSize: '18px', color: '#555', margin: '0 0 15px 0' },
    tagRow: { display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '20px' },
    tag: { padding: '5px 12px', borderRadius: '15px', fontSize: '14px', backgroundColor: '#e9ecef', color: '#495057', fontWeight: '500' },
    salaryTag: { padding: '5px 12px', borderRadius: '15px', fontSize: '14px', backgroundColor: '#d4edda', color: '#155724', fontWeight: 'bold' },
    sectionTitle: { color: '#333', marginTop: '30px', borderBottom: '1px solid #eee', paddingBottom: '5px' },
    descriptionBox: { backgroundColor: '#f9f9f9', padding: '20px', borderRadius: '8px', lineHeight: '1.6', color: '#444', whiteSpace: 'pre-wrap' },
    techTag: { padding: '5px 10px', borderRadius: '5px', backgroundColor: '#e2e3e5', color: '#383d41', border: '1px solid #d6d8db', marginRight: '8px', display: 'inline-block', marginBottom: '8px' },
    
    // Form Styles
    applyBox: { marginTop: '40px', padding: '25px', backgroundColor: '#f8f9fa', borderRadius: '10px', border: '1px solid #dee2e6' },
    input: { width: '100%', padding: '10px', borderRadius: '5px', border: '1px solid #ccc', boxSizing: 'border-box', marginBottom: '15px', color: '#333' },
    textarea: { width: '100%', padding: '15px', borderRadius: '8px', border: '1px solid #ced4da', boxSizing: 'border-box', minHeight: '150px', color: '#333', marginBottom: '15px', fontFamily: 'inherit' },
    submitBtn: { backgroundColor: '#28a745', color: 'white', border: 'none', padding: '15px 30px', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold', fontSize: '16px', width: '100%' },
    externalApplyBtn: { display: 'block', textAlign: 'center', backgroundColor: '#007bff', color: 'white', padding: '15px', borderRadius: '8px', textDecoration: 'none', fontWeight: 'bold', fontSize: '18px', marginTop: '40px' }
  };

  return (
    <div style={styles.container}>
      <Link to="/jobs" style={styles.backLink}>&larr; Back to all jobs</Link>
      
      <div style={styles.headerBox}>
        <h1 style={styles.title}>{job.title}</h1>
        <div style={styles.companyLine}>🏢 <strong>{companyName}</strong> &bull; 📍 {locationName}</div>
        
        <div style={styles.tagRow}>
          <span style={styles.tag}>📁 {categoryName}</span>
          {job.country && <span style={styles.tag}>🌍 {job.country}</span>}
          {job.experienceLevel && <span style={{...styles.tag, backgroundColor: '#cff4fc', color: '#055160'}}>⭐ {job.experienceLevel}</span>}
          {formattedSalary && <span style={styles.salaryTag}>💰 {formattedSalary}</span>}
        </div>
      </div>

      {job.programmingLanguages && job.programmingLanguages.length > 0 && (
        <div>
          <h3 style={styles.sectionTitle}>Technical Stack</h3>
          <div>
            {job.programmingLanguages.map((lang, idx) => (
              <span key={idx} style={styles.techTag}>{lang}</span>
            ))}
          </div>
        </div>
      )}

      <h3 style={styles.sectionTitle}>Job Description</h3>
      <div style={styles.descriptionBox}>
        {job.description}
      </div>

      {/* --- APPLY SECTION --- */}
      {isInternalJob ? (
          <div style={styles.applyBox}>
              <h3 style={{marginTop: 0, color: '#333'}}>Apply for this position</h3>
              
              {applySuccess ? (
                  <div style={{padding: '20px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '5px', textAlign: 'center', fontWeight: 'bold'}}>
                      🎉 Application submitted successfully! The company will review your profile.
                  </div>
              ) : (
                  <>
                      {/* FIX: Schimbat 'showForm' in 'showApplyForm' */}
                      {!showApplyForm && (
                          <button onClick={() => setShowApplyForm(true)} style={styles.submitBtn}>
                             Apply Now on Platform
                          </button>
                      )}
                      
                      {showApplyForm && (
                          <form onSubmit={handleSubmitApplication}>
                              <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold', color: '#333'}}>Your Full Name</label>
                              <input 
                                  style={styles.input} 
                                  type="text" 
                                  required 
                                  value={applicantName}
                                  onChange={(e) => setApplicantName(e.target.value)}
                                  placeholder="e.g. John Doe" 
                              />

                              <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold', color: '#333'}}>Paste your CV / Profile Summary</label>
                              <p style={{fontSize: '12px', color: '#666', marginTop: 0}}>Our AI will evaluate your profile against the job description to help the company process your application faster.</p>
                              <textarea 
                                  style={styles.textarea} 
                                  required
                                  value={cvText}
                                  onChange={(e) => setCvText(e.target.value)}
                                  placeholder="Paste your experience, skills, education..." 
                              />

                              <div style={{display: 'flex', gap: '10px'}}>
                                  <button type="button" onClick={() => setShowApplyForm(false)} style={{padding: '15px', backgroundColor: '#6c757d', color: 'white', border: 'none', borderRadius: '5px', cursor: 'pointer', fontWeight: 'bold'}}>
                                      Cancel
                                  </button>
                                  <button type="submit" disabled={isSubmitting} style={{...styles.submitBtn, flex: 1, opacity: isSubmitting ? 0.7 : 1}}>
                                      {isSubmitting ? 'Processing Application & AI Evaluation...' : 'Submit Application'}
                                  </button>
                              </div>
                          </form>
                      )}
                  </>
              )}
          </div>
      ) : (
          <a href={job.url} target="_blank" rel="noopener noreferrer" style={styles.externalApplyBtn}>
            Apply Externally ↗
          </a>
      )}
    </div>
  );
};

export default JobDetailsPage;