import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import JobCard from '../components/JobCard';
import Pagination from '../components/Pagination';
import authHeader from '../services/auth-header'; 

const JobsPage = () => {
  const location = useLocation();
  const searchFromState = location.state?.search || '';
  const countryFromState = location.state?.country || '';
  const categoryFromState = location.state?.category || '';

  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [jobsPerPage] = useState(20);

  useEffect(() => {
    const loadJobs = async () => {
      setLoading(true);
      const url = new URL('/api/jobs', window.location.origin);
      url.searchParams.append('page', currentPage);
      url.searchParams.append('size', jobsPerPage);
      
      if (searchFromState) {
        url.searchParams.append('search', searchFromState);
      }
      if (countryFromState) {
        url.searchParams.append('country', countryFromState);
      }
      if (categoryFromState) {
        url.searchParams.append('category', categoryFromState);
      }

      try {
        const response = await fetch(url, { headers: authHeader() });
        if (!response.ok) throw new Error(`Server responded with ${response.status}`);
        const data = await response.json();
        
        setJobs(data.content || []);
        setTotalPages(data.totalPages || 0);

      } catch (err) {
        console.error("Failed to load jobs from server:", err);
        setJobs([]);
        setTotalPages(0);
      } finally {
        setLoading(false);
      }
    };
    loadJobs();
  }, [currentPage, searchFromState, countryFromState, categoryFromState, jobsPerPage]);

  const handlePageChange = (pageNumber) => {
    setCurrentPage(pageNumber);
  };

  const getHeader = () => {
    if (searchFromState) return `Search results for "${searchFromState}"`;
    if (countryFromState) return `Jobs in ${countryFromState}`;
    if (categoryFromState) return `Jobs in ${categoryFromState}`;
    return 'Find Your Dream Job';
  };

  if (loading) return <div style={{textAlign: 'center', marginTop: '50px', fontSize: '1.2rem'}}>Loading amazing jobs...</div>;

  return (
    <div style={{ padding: '40px', backgroundColor: '#f4f7f6', minHeight: '100vh' }}>
      <h1 style={{ textAlign: 'center', margin: '0 auto 40px auto', color: '#333', maxWidth: '800px', lineHeight: '1.4' }}>{getHeader()}</h1>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '900px', margin: '0 auto' }}>
        {jobs.length > 0 ? jobs.map(job => (
          <JobCard key={job.id} job={job} />
        )) : <p style={{textAlign: 'center', color: '#777', marginTop: '30px'}}>No jobs found matching your criteria.</p>}
      </div>

      <Pagination 
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={handlePageChange}
      />
    </div>
  );
};

export default JobsPage;