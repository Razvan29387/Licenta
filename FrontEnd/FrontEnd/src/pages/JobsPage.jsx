import React, { useState, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';

const JobsPage = () => {
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
  const [searchTerm, setSearchTerm] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [selectedCompany, setSelectedCompany] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedLocation, setSelectedLocation] = useState('');
  const [selectedCountry, setSelectedCountry] = useState('');
  const [selectedExperience, setSelectedExperience] = useState('');

  // --- FETCH DATA ---
  useEffect(() => {
    const loadJobs = async () => {
      try {
        const response = await fetch('/api/jobs');
        if (!response.ok) throw new Error(`Server responded with ${response.status}`);
        const data = await response.json();
        
        if (Array.isArray(data)) {
            setJobs(data);
            
            try {
                const limitedDataToCache = data.slice(0, 50); 
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
  
  // Format salary nicely
  const formatSalary = (min, max, period) => {
      if (!min && !max) return null;
      
      const formatNumber = (num) => {
          return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(num);
      };

      let salaryStr = "";
      if (min && max && min !== max) {
          salaryStr = `${formatNumber(min)} - ${formatNumber(max)}`;
      } else if (min) {
          salaryStr = formatNumber(min);
      } else {
          salaryStr = formatNumber(max);
      }
      
      if (period) {
          salaryStr += ` / ${period}`;
      }
      return salaryStr;
  };

  // --- FILTER LOGIC ---
  const uniqueCompanies = useMemo(() => [...new Set(jobs.map(getCompanyName))].sort(), [jobs]);
  const uniqueCategories = useMemo(() => [...new Set(jobs.map(getCategoryName))].sort(), [jobs]);
  const uniqueLocations = useMemo(() => [...new Set(jobs.map(getLocationName))].sort(), [jobs]);
  const uniqueCountries = useMemo(() => [...new Set(jobs.map(getCountryName))].sort(), [jobs]);
  
  const uniqueExperiences = useMemo(() => {
      const expLevels = jobs.map(j => j.experienceLevel).filter(e => e && e.trim() !== '');
      return [...new Set(expLevels)].sort();
  }, [jobs]);

  const filteredJobs = useMemo(() => {
    return jobs.filter(job => {
      const title = job.title || "";
      const matchSearch = title.toLowerCase().includes(searchTerm.toLowerCase()) || 
                          getCompanyName(job).toLowerCase().includes(searchTerm.toLowerCase());
      const matchCompany = selectedCompany ? getCompanyName(job) === selectedCompany : true;
      const matchCategory = selectedCategory ? getCategoryName(job) === selectedCategory : true;
      const matchLocation = selectedLocation ? getLocationName(job) === selectedLocation : true;
      const matchCountry = selectedCountry ? getCountryName(job) === selectedCountry : true;
      const matchExperience = selectedExperience ? job.experienceLevel === selectedExperience : true;

      return matchSearch && matchCompany && matchCategory && matchLocation && matchCountry && matchExperience;
    });
  }, [jobs, searchTerm, selectedCompany, selectedCategory, selectedLocation, selectedCountry, selectedExperience]);

  // Reset page on filter change
  useEffect(() => setCurrentJobPage(1), [searchTerm, selectedCompany, selectedCategory, selectedLocation, selectedCountry, selectedExperience]);

  // --- PAGINATION LOGIC ---
  const indexOfLastJob = currentJobPage * jobsPerPage;
  const indexOfFirstJob = indexOfLastJob - jobsPerPage;
  const currentJobs = filteredJobs.slice(indexOfFirstJob, indexOfLastJob);
  const totalPages = Math.ceil(filteredJobs.length / jobsPerPage);

  const handleJumpToPage = () => {
    const pageNumber = parseInt(jumpToPage, 10);
    if (!isNaN(pageNumber) && pageNumber >= 1 && pageNumber <= totalPages) {
      setCurrentJobPage(pageNumber);
      setJumpToPage('');
    } else {
      alert(`Please enter a valid page number between 1 and ${totalPages}`);
    }
  };

  // --- STYLES ---
  const styles = {
    container: { padding: '40px', backgroundColor: '#f4f7f6', minHeight: '100vh', fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif" },
    header: { textAlign: 'center', marginBottom: '40px', color: '#333' },
    searchContainer: { display: 'flex', justifyContent: 'center', gap: '10px', marginBottom: '20px' },
    searchInput: { padding: '12px 20px', width: '400px', borderRadius: '30px', border: '1px solid #ddd', fontSize: '16px', outline: 'none', boxShadow: '0 2px 5px rgba(0,0,0,0.05)', color: '#333' },
    button: { padding: '12px 25px', borderRadius: '30px', border: 'none', backgroundColor: '#007bff', color: 'white', cursor: 'pointer', fontSize: '16px', fontWeight: 'bold', transition: 'background 0.3s' },
    filterButton: { padding: '12px 20px', borderRadius: '30px', border: '1px solid #007bff', backgroundColor: 'white', color: '#007bff', cursor: 'pointer', fontWeight: 'bold' },
    filtersPanel: { backgroundColor: 'white', padding: '20px', borderRadius: '15px', boxShadow: '0 4px 15px rgba(0,0,0,0.05)', marginBottom: '30px', display: 'flex', gap: '20px', flexWrap: 'wrap', justifyContent: 'center', color: '#333' },
    select: { padding: '10px', borderRadius: '8px', border: '1px solid #ddd', minWidth: '180px', color: '#333' },
    listContainer: { display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '900px', margin: '0 auto' },
    card: { backgroundColor: 'white', borderRadius: '12px', padding: '25px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)', transition: 'transform 0.2s, box-shadow 0.2s', display: 'flex', flexDirection: 'column', borderLeft: '5px solid #007bff' },
    cardHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' },
    cardTitle: { fontSize: '20px', fontWeight: 'bold', margin: '0 0 5px 0', color: '#2c3e50' },
    metaContainer: { display: 'flex', flexWrap: 'wrap', gap: '15px', marginBottom: '15px', fontSize: '14px', color: '#555' },
    metaItem: { display: 'flex', alignItems: 'center', gap: '5px', fontWeight: '500' },
    description: { color: '#444', fontSize: '15px', lineHeight: '1.6', marginBottom: '20px' },
    tag: { padding: '4px 10px', borderRadius: '15px', fontSize: '12px', fontWeight: '600', backgroundColor: '#e3f2fd', color: '#0d47a1' },
    countryTag: { padding: '4px 10px', borderRadius: '15px', fontSize: '12px', fontWeight: '600', backgroundColor: '#fff3cd', color: '#856404', marginLeft: '5px' },
    expTag: { padding: '4px 10px', borderRadius: '15px', fontSize: '12px', fontWeight: '600', backgroundColor: '#cff4fc', color: '#055160', marginLeft: '5px' },
    salaryTag: { padding: '4px 10px', borderRadius: '15px', fontSize: '12px', fontWeight: '600', backgroundColor: '#d4edda', color: '#155724', marginLeft: '5px' }, // New Style for Salary
    techTag: { padding: '3px 8px', borderRadius: '10px', fontSize: '11px', backgroundColor: '#f8f9fa', color: '#495057', border: '1px solid #ced4da', marginRight: '5px', display: 'inline-block', marginTop: '5px' },
    applyBtn: { alignSelf: 'flex-start', backgroundColor: '#28a745', color: 'white', padding: '10px 25px', borderRadius: '6px', textDecoration: 'none', fontWeight: 'bold', fontSize: '14px' },
    paginationContainer: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '15px', marginTop: '40px', paddingBottom: '20px' },
    paginationControls: { display: 'flex', justifyContent: 'center', gap: '10px', alignItems: 'center' },
    pageBtn: { padding: '8px 16px', border: '1px solid #ddd', borderRadius: '5px', backgroundColor: 'white', cursor: 'pointer', color: '#333' },
    jumpInput: { width: '50px', padding: '8px', borderRadius: '5px', border: '1px solid #ddd', textAlign: 'center', marginRight: '5px', color: '#333' }
  };

  if (loading && jobs.length === 0) return <div style={{textAlign: 'center', marginTop: '50px'}}>Loading amazing jobs...</div>;

  return (
    <div style={styles.container}>
      <h1 style={styles.header}>Find Your Dream Job</h1>

      {/* --- SEARCH & FILTER BAR --- */}
      <div style={styles.searchContainer}>
        <input 
          style={styles.searchInput} 
          type="text" 
          placeholder="Search by title or company..." 
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        <button style={styles.filterButton} onClick={() => setShowFilters(!showFilters)}>
          {showFilters ? 'Hide Filters' : 'Filter Options'}
        </button>
      </div>

      {/* --- FILTERS --- */}
      {showFilters && (
        <div style={styles.filtersPanel}>
          <div>
            <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold'}}>Country</label>
            <select style={styles.select} value={selectedCountry} onChange={(e) => setSelectedCountry(e.target.value)}>
              <option value="">All Countries</option>
              {uniqueCountries.map((c, i) => <option key={i} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold'}}>Company</label>
            <select style={styles.select} value={selectedCompany} onChange={(e) => setSelectedCompany(e.target.value)}>
              <option value="">All Companies</option>
              {uniqueCompanies.map((c, i) => <option key={i} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold'}}>Category</label>
            <select style={styles.select} value={selectedCategory} onChange={(e) => setSelectedCategory(e.target.value)}>
              <option value="">All Categories</option>
              {uniqueCategories.map((c, i) => <option key={i} value={c}>{c}</option>)}
            </select>
          </div>
          
          {/* Experience Filter */}
          {uniqueExperiences.length > 0 && (
            <div>
              <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold'}}>Experience</label>
              <select style={styles.select} value={selectedExperience} onChange={(e) => setSelectedExperience(e.target.value)}>
                <option value="">Any Level</option>
                {uniqueExperiences.map((e, i) => <option key={i} value={e}>{e}</option>)}
              </select>
            </div>
          )}

          <div>
            <label style={{display: 'block', marginBottom: '5px', fontWeight: 'bold'}}>Location</label>
            <select style={styles.select} value={selectedLocation} onChange={(e) => setSelectedLocation(e.target.value)}>
              <option value="">All Locations</option>
              {uniqueLocations.map((l, i) => <option key={i} value={l}>{l}</option>)}
            </select>
          </div>
          
          <div style={{display: 'flex', alignItems: 'flex-end'}}>
             <button 
               style={{...styles.button, backgroundColor: '#dc3545', color: 'white', padding: '10px 20px'}} 
               onClick={() => {
                 setSelectedCountry('');
                 setSelectedCompany('');
                 setSelectedCategory('');
                 setSelectedLocation('');
                 setSelectedExperience('');
                 setSearchTerm('');
               }}
             >
               Clear All
             </button>
          </div>
        </div>
      )}

      {/* --- JOBS LIST --- */}
      <div style={styles.listContainer}>
        {currentJobs.map(job => {
          const formattedSalary = formatSalary(job.salaryMin, job.salaryMax, job.salaryPeriod);
          return (
          <div key={job.id || Math.random()} style={styles.card}>
            <div style={styles.cardHeader}>
                 <h3 style={styles.cardTitle}>
                    <Link to={`/jobs/${job.id}`} style={{textDecoration: 'none', color: 'inherit'}}>
                        {job.title || 'Untitled Job'}
                    </Link>
                 </h3>
                 <div>
                    <span style={styles.tag}>{getCategoryName(job)}</span>
                    <span style={styles.countryTag}>🌍 {getCountryName(job)}</span>
                    {job.experienceLevel && <span style={styles.expTag}>⭐ {job.experienceLevel}</span>}
                    {/* Afișează badge-ul de salariu dacă există date */}
                    {formattedSalary && <span style={styles.salaryTag}>💰 {formattedSalary}</span>}
                 </div>
            </div>

            <div style={styles.metaContainer}>
                <div style={styles.metaItem}>🏢 {getCompanyName(job)}</div>
                <div style={styles.metaItem}>📍 {getLocationName(job)}</div>
            </div>

            {/* Afișează limbajele de programare dacă există */}
            {job.programmingLanguages && job.programmingLanguages.length > 0 && (
              <div style={{marginBottom: '15px'}}>
                <span style={{fontSize: '13px', fontWeight: 'bold', color: '#555', marginRight: '5px'}}>Stack:</span>
                {job.programmingLanguages.map((lang, idx) => (
                  <span key={idx} style={styles.techTag}>{lang}</span>
                ))}
              </div>
            )}

            <p style={styles.description}>
                {getShortDescription(job.description)}
            </p>

            <a href={job.url || job.redirect_url || '#'} target="_blank" rel="noopener noreferrer" style={styles.applyBtn}>
              {job.url ? 'Apply Externally ↗' : 'Apply on Platform'}
            </a>
          </div>
        )})}
      </div>

      {currentJobs.length === 0 && <p style={{textAlign: 'center', color: '#777', marginTop: '30px'}}>No jobs found matching your criteria.</p>}

      {/* --- PAGINATION & JUMP --- */}
      {totalPages > 1 && (
        <div style={styles.paginationContainer}>
            <div style={styles.paginationControls}>
              <button style={styles.pageBtn} onClick={() => setCurrentJobPage(1)} disabled={currentJobPage === 1}>First</button>
              <button style={styles.pageBtn} onClick={() => setCurrentJobPage(p => Math.max(1, p - 1))} disabled={currentJobPage === 1}>Prev</button>
              
              <span style={{padding: '8px 12px', fontWeight: 'bold', color: '#333'}}>
                Page {currentJobPage} of {totalPages}
              </span>

              <button style={styles.pageBtn} onClick={() => setCurrentJobPage(p => Math.min(totalPages, p + 1))} disabled={currentJobPage === totalPages}>Next</button>
              <button style={styles.pageBtn} onClick={() => setCurrentJobPage(totalPages)} disabled={currentJobPage === totalPages}>Last</button>
            </div>

            <div style={{display: 'flex', alignItems: 'center'}}>
                <span style={{marginRight: '10px', color: '#555'}}>Go to page:</span>
                <input 
                    type="number" 
                    min="1" 
                    max={totalPages}
                    value={jumpToPage}
                    onChange={(e) => setJumpToPage(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && handleJumpToPage()}
                    style={styles.jumpInput}
                />
                <button 
                    onClick={handleJumpToPage}
                    style={{...styles.pageBtn, backgroundColor: '#007bff', color: 'white', borderColor: '#007bff'}}
                >
                    Go
                </button>
            </div>
        </div>
      )}
    </div>
  );
};

export default JobsPage;