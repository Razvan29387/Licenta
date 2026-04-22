import React, { useState, useEffect, useMemo } from 'react';
import { Link, useLocation } from 'react-router-dom';

const JobsPage = () => {
  const location = useLocation();
  const searchState = location.state?.search || '';
  const countryState = location.state?.country || '';

  // --- STATE INITIALIZATION ---
  const [jobs, setJobs] = useState(() => {
    try {
        const savedJobs = localStorage.getItem('cached_jobs');
        return savedJobs ? JSON.parse(savedJobs) : [];
    } catch (e) {
        console.error("Failed to parse jobs from localStorage", e);
        return [];
    }
  });

  const [loading, setLoading] = useState(jobs.length === 0);
  
  // State pentru paginare
  const [currentJobPage, setCurrentJobPage] = useState(1);
  const [jobsPerPage] = useState(7);
  const [jumpToPage, setJumpToPage] = useState('');

  // State pentru căutare și filtre
  const [searchTerm, setSearchTerm] = useState(searchState);
  const [showFilters, setShowFilters] = useState(false);
  const [selectedCompany, setSelectedCompany] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedLocation, setSelectedLocation] = useState('');
  const [selectedCountry, setSelectedCountry] = useState(countryState);
  const [selectedExperience, setSelectedExperience] = useState('');

  // Sincronizare cu search query venit din alte pagini (Dashboard)
  useEffect(() => {
    if (location.state?.search) {
      setSearchTerm(location.state.search);
    }
    if (location.state?.country) {
      setSelectedCountry(location.state.country);
    }
  }, [location.state]);

  // --- FETCH DATA ---
  useEffect(() => {
    const loadJobs = async () => {
      setLoading(true);
      try {
        const response = await fetch('/api/jobs');
        if (!response.ok) throw new Error(`Server responded with ${response.status}`);
        const data = await response.json();
        
        if (Array.isArray(data)) {
            setJobs(data);
            // Cache a smaller portion of data to avoid storage issues
            try {
                const limitedDataToCache = data.slice(0, 100); 
                localStorage.setItem('cached_jobs', JSON.stringify(limitedDataToCache));
            } catch (storageError) {
                console.warn("Could not save to localStorage, it might be full.", storageError);
                localStorage.removeItem('cached_jobs');
            }
        } else {
             console.error("Expected array from server but got:", typeof data);
        }

      } catch (err) {
        console.error("Failed to load jobs from server, will rely on cached data if available:", err);
      } finally {
        setLoading(false);
      }
    };
    loadJobs();
  }, []);

  // --- HELPER FUNCTIONS ---
  const getCompanyName = (job) => {
    if (!job.company) return "Unknown Company";
    return typeof job.company === 'string' ? job.company : (job.company.name || job.company.display_name || "Unknown Company");
  };

  const getCategoryName = (job) => {
    if (!job.category) return "Uncategorized";
    return typeof job.category === 'string' ? job.category : (job.category.label || "Uncategorized");
  };

  const getLocationName = (job) => {
      if (!job.location) return "Unknown Location";
      return typeof job.location === 'string' ? job.location : (job.location.display_name || "Unknown Location");
  };

  const getCountryName = (job) => {
      return job.country || "Unknown Country";
  }

  const getShortDescription = (description) => {
      if (!description) return "No description available.";
      const cleanText = description.replace(/<[^>]*>?/gm, '');
      return cleanText.length > 250 ? cleanText.substring(0, 250) + "..." : cleanText;
  };
  
  const formatSalary = (min, max, period) => {
      if (!min && !max) return null;
      const formatNumber = (num) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(num);
      let salaryStr = min && max && min !== max ? `${formatNumber(min)} - ${formatNumber(max)}` : (min ? formatNumber(min) : formatNumber(max));
      if (period) salaryStr += ` / ${period}`;
      return salaryStr;
  };

  // --- FILTER LOGIC ---
  const uniqueCompanies = useMemo(() => [...new Set(jobs.map(getCompanyName))].sort(), [jobs]);
  const uniqueCategories = useMemo(() => [...new Set(jobs.map(getCategoryName))].sort(), [jobs]);
  const uniqueLocations = useMemo(() => [...new Set(jobs.map(getLocationName))].sort(), [jobs]);
  const uniqueCountries = useMemo(() => [...new Set(jobs.map(getCountryName))].sort(), [jobs]);
  const uniqueExperiences = useMemo(() => [...new Set(jobs.map(j => j.experienceLevel).filter(Boolean))].sort(), [jobs]);

  const filteredJobs = useMemo(() => {
    return jobs.filter(job => {
      const title = job.title || "";
      const matchSearch = searchTerm ? (title.toLowerCase().includes(searchTerm.toLowerCase()) || getCompanyName(job).toLowerCase().includes(searchTerm.toLowerCase())) : true;
      const matchCompany = selectedCompany ? getCompanyName(job) === selectedCompany : true;
      const matchCategory = selectedCategory ? getCategoryName(job) === selectedCategory : true;
      const matchLocation = selectedLocation ? getLocationName(job) === selectedLocation : true;
      const matchCountry = selectedCountry ? getCountryName(job) === selectedCountry : true;
      const matchExperience = selectedExperience ? job.experienceLevel === selectedExperience : true;
      return matchSearch && matchCompany && matchCategory && matchLocation && matchCountry && matchExperience;
    });
  }, [jobs, searchTerm, selectedCompany, selectedCategory, selectedLocation, selectedCountry, selectedExperience]);

  useEffect(() => setCurrentJobPage(1), [searchTerm, selectedCompany, selectedCategory, selectedLocation, selectedCountry, selectedExperience]);

  // --- PAGINATION LOGIC ---
  const indexOfLastJob = currentJobPage * jobsPerPage;
  const indexOfFirstJob = indexOfLastJob - jobsPerPage;
  const currentJobs = filteredJobs.slice(indexOfFirstJob, indexOfLastJob);
  const totalPages = Math.ceil(filteredJobs.length / jobsPerPage);

  const handleJumpToPage = () => {
    const pageNumber = parseInt(jumpToPage, 10);
    if (pageNumber >= 1 && pageNumber <= totalPages) {
      setCurrentJobPage(pageNumber);
      setJumpToPage('');
    } else {
      alert(`Please enter a valid page number between 1 and ${totalPages}`);
    }
  };

  // --- STYLES (minimalist, as most styles should come from a CSS file) ---
  const styles = {
    container: { padding: '40px', backgroundColor: '#f4f7f6', minHeight: '100vh', fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif" },
    header: { textAlign: 'center', marginBottom: '40px', color: '#333' },
    searchContainer: { display: 'flex', justifyContent: 'center', gap: '10px', marginBottom: '20px' },
    searchInput: { padding: '12px 20px', width: '400px', borderRadius: '30px', border: '1px solid #ddd', fontSize: '16px', outline: 'none' },
    filterButton: { padding: '12px 20px', borderRadius: '30px', border: '1px solid #007bff', backgroundColor: 'white', color: '#007bff', cursor: 'pointer', fontWeight: 'bold' },
    filtersPanel: { backgroundColor: 'white', padding: '20px', borderRadius: '15px', boxShadow: '0 4px 15px rgba(0,0,0,0.05)', marginBottom: '30px', display: 'flex', gap: '20px', flexWrap: 'wrap', justifyContent: 'center' },
    select: { padding: '10px', borderRadius: '8px', border: '1px solid #ddd', minWidth: '180px' },
    listContainer: { display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '900px', margin: '0 auto' },
    card: { backgroundColor: 'white', borderRadius: '12px', padding: '25px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)', borderLeft: '5px solid #007bff' },
    cardTitle: { fontSize: '20px', fontWeight: 'bold', margin: '0 0 5px 0', color: '#2c3e50' },
    metaContainer: { display: 'flex', flexWrap: 'wrap', gap: '15px', marginBottom: '15px', fontSize: '14px', color: '#555' },
    description: { color: '#444', fontSize: '15px', lineHeight: '1.6', marginBottom: '20px' },
    tag: { padding: '4px 10px', borderRadius: '15px', fontSize: '12px', fontWeight: '600' },
    applyBtn: { alignSelf: 'flex-start', backgroundColor: '#28a745', color: 'white', padding: '10px 25px', borderRadius: '6px', textDecoration: 'none', fontWeight: 'bold' },
    paginationContainer: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px', marginTop: '40px' },
  };

  if (loading) return <div style={{textAlign: 'center', marginTop: '50px', fontSize: '1.2rem'}}>Loading amazing jobs...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Find Your Dream Job</h1>

      <div style={styles.searchContainer}>
        <input style={styles.searchInput} type="text" placeholder="Search by title or company..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
        <button style={styles.filterButton} onClick={() => setShowFilters(!showFilters)}>{showFilters ? 'Hide Filters' : 'Show Filters'}</button>
      </div>

      {showFilters && (
        <div style={styles.filtersPanel}>
          <select style={styles.select} value={selectedCountry} onChange={(e) => setSelectedCountry(e.target.value)}>
            <option value="">All Countries</option>
            {uniqueCountries.map((c, i) => <option key={i} value={c}>{c}</option>)}
          </select>
          <select style={styles.select} value={selectedCompany} onChange={(e) => setSelectedCompany(e.target.value)}>
            <option value="">All Companies</option>
            {uniqueCompanies.map((c, i) => <option key={i} value={c}>{c}</option>)}
          </select>
          <select style={styles.select} value={selectedCategory} onChange={(e) => setSelectedCategory(e.target.value)}>
            <option value="">All Categories</option>
            {uniqueCategories.map((c, i) => <option key={i} value={c}>{c}</option>)}
          </select>
          {uniqueExperiences.length > 0 && (
            <select style={styles.select} value={selectedExperience} onChange={(e) => setSelectedExperience(e.target.value)}>
              <option value="">Any Experience</option>
              {uniqueExperiences.map((e, i) => <option key={i} value={e}>{e}</option>)}
            </select>
          )}
        </div>
      )}

      <div style={styles.listContainer}>
        {currentJobs.length > 0 ? currentJobs.map(job => (
          <div key={job.id || Math.random()} style={styles.card}>
            <h3 style={styles.cardTitle}><Link to={`/jobs/${job.id}`} style={{textDecoration: 'none', color: 'inherit'}}>{job.title || 'Untitled Job'}</Link></h3>
            <div style={styles.metaContainer}>
                <span>🏢 {getCompanyName(job)}</span>
                <span>📍 {getLocationName(job)}</span>
                <span style={{...styles.tag, backgroundColor: '#e3f2fd', color: '#0d47a1'}}>📁 {getCategoryName(job)}</span>
                <span style={{...styles.tag, backgroundColor: '#fff3cd', color: '#856404'}}>🌍 {getCountryName(job)}</span>
            </div>
            <p style={styles.description}>{getShortDescription(job.description)}</p>
            <a href={job.url || '#'} target="_blank" rel="noopener noreferrer" style={styles.applyBtn}>
              {job.url ? 'Apply Externally ↗' : 'Apply on Platform'}
            </a>
          </div>
        )) : <p style={{textAlign: 'center', color: '#777', marginTop: '30px'}}>No jobs found matching your criteria.</p>}
      </div>

      {totalPages > 1 && (
        <div style={styles.paginationContainer}>
          {/* Pagination controls can be added back here if needed */}
        </div>
      )}
    </div>
  );
};

export default JobsPage;