import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import '../styles/DashboardPage.css'; // Importă fișierul CSS

const DashboardPage = () => {
  const [stats, setStats] = useState({ totalJobs: 0, totalCompanies: 0, totalApplications: 0, jobsByCountry: [] });
  const [searchTerm, setSearchTerm] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await fetch('/api/stats');
        if (response.ok) {
          const data = await response.json();
          setStats({
              totalJobs: data.totalJobs || 0,
              totalCompanies: data.totalCompanies || 0,
              totalApplications: data.totalApplications || 0,
              jobsByCountry: data.jobsByCountry || []
          });
        }
      } catch (error) {
        console.error("Failed to fetch stats:", error);
      }
    };

    fetchStats();
  }, []);

  const handleSearch = () => {
    navigate('/jobs', { state: { search: searchTerm.trim() } });
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch();
  };

  const handleCountryClick = (countryName) => {
      navigate('/jobs', { state: { country: countryName } });
  };

  return (
    <div className="dashboard-container">
      <div className="hero-section">
        
        {/* Admin Link */}
        <Link to="/admin" className="admin-link" title="Admin Tasks">
          ⚙️
        </Link>

        <h1 className="hero-title">Find Your Next Opportunity</h1>
        <p className="hero-subtitle">
          Explore over <strong>{stats.totalJobs}</strong> jobs from <strong>{stats.totalCompanies}</strong> companies and <strong>{stats.totalApplications}</strong> applications worldwide.
        </p>
        
        <div className="search-container">
          <input 
            type="text" 
            className="search-input"
            placeholder="Job title, keywords, or company" 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button 
            className="search-button"
            onClick={handleSearch}
          >
            Search
          </button>
        </div>

        <div className="stats-container">
            <div className="stat-box">
                <h2 className="stat-number blue">{stats.totalJobs}</h2>
                <p className="stat-label">Active Jobs</p>
            </div>
            <div className="stat-box">
                <h2 className="stat-number cyan">{stats.totalCompanies}</h2>
                <p className="stat-label">Companies</p>
            </div>
            <div className="stat-box">
                <h2 className="stat-number orange">{stats.totalApplications}</h2>
                <p className="stat-label">Applications</p>
            </div>
        </div>

        {stats.jobsByCountry && stats.jobsByCountry.length > 0 && (
            <div className="locations-section">
                <h3 className="locations-title">
                    Popular Locations
                </h3>
                <div className="locations-grid">
                    {stats.jobsByCountry.map((item, index) => (
                        <div 
                            key={index} 
                            className="location-card"
                            onClick={() => handleCountryClick(item.country)}
                        >
                            <span>🌍</span>
                            <span className="country-name">{item.country}</span>
                            <span className="job-count">{item.count} jobs</span>
                        </div>
                    ))}
                </div>
            </div>
        )}
      </div>
    </div>
  );
};

export default DashboardPage;