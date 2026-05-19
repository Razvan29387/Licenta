import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import JobCard from '../components/JobCard';
import Pagination from '../components/Pagination';

const JobsPage = () => {
  const location = useLocation();
  const countryFromState = location.state?.country || '';
  const categoryFromState = location.state?.category || '';
  const searchFromState = location.state?.search || '';

  // --- STATE INITIALIZATION ---
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // State for pagination
  const [currentPage, setCurrentPage] = useState(0); // API is 0-indexed
  const [totalPages, setTotalPages] = useState(0);
  const [jobsPerPage] = useState(20);

  // State for filters
  const [searchTerm, setSearchTerm] = useState(searchFromState);
  const [selectedCountry, setSelectedCountry] = useState(countryFromState);
  const [selectedCategory, setSelectedCategory] = useState(categoryFromState);
  // ... other filters can be added here

  // --- FETCH DATA ---
  useEffect(() => {
    const loadJobs = async () => {
      setLoading(true);
      const url = new URL('/api/jobs', window.location.origin);
      url.searchParams.append('page', currentPage);
      url.searchParams.append('size', jobsPerPage);
      
      if (searchTerm) {
        url.searchParams.append('search', searchTerm);
      } else {
        if (selectedCountry) {
          url.searchParams.append('country', selectedCountry);
        }
        if (selectedCategory) {
          url.searchParams.append('category', selectedCategory);
        }
      }

      try {
        const response = await fetch(url);
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
  }, [currentPage, searchTerm, selectedCountry, selectedCategory, jobsPerPage]); // Re-fetch when filters change

  // --- HANDLERS ---
  const handlePageChange = (pageNumber) => {
    setCurrentPage(pageNumber);
  };

  // Reset page to 0 when filters change
  useEffect(() => {
    setCurrentPage(0);
  }, [searchTerm, selectedCountry, selectedCategory]);

  // --- STYLES ---
  const styles = {
    container: { padding: '40px', backgroundColor: '#f4f7f6', minHeight: '100vh', fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif" },
    header: { textAlign: 'center', marginBottom: '40px', color: '#333' },
    listContainer: { display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '900px', margin: '0 auto' },
  };

  const getHeader = () => {
    if (searchTerm) return `Search results for "${searchTerm}"`;
    if (selectedCountry) return `Jobs in ${selectedCountry}`;
    if (selectedCategory) return `Jobs in ${selectedCategory}`;
    return 'Find Your Dream Job';
  };

  if (loading) return <div style={{textAlign: 'center', marginTop: '50px', fontSize: '1.2rem'}}>Loading amazing jobs...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>{getHeader()}</h1>

      <div style={styles.listContainer}>
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