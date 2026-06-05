import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import '../styles/DashboardPage.css';

const DashboardPage = () => {
  const [stats, setStats] = useState({ 
    totalJobs: 0, 
    totalCompanies: 0, 
    totalApplications: 0, 
    jobsByCountry: [],
    jobsByCategory: [],
    jobsByLanguage: []
  });
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
              jobsByCountry: data.jobsByCountry || [],
              jobsByCategory: data.jobsByCategory || [],
              jobsByLanguage: data.jobsByLanguage || []
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

  const handleFilterClick = (filterType, value) => {
      navigate('/jobs', { state: { [filterType]: value } });
  };

  const getFlagForCountry = (countryName) => {
    const flags = {
      'gb': '🇬🇧', 'uk': '🇬🇧', 'united kingdom': '🇬🇧',
      'us': '🇺🇸', 'usa': '🇺🇸', 'united states': '🇺🇸',
      'de': '🇩🇪', 'germany': '🇩🇪',
      'fr': '🇫🇷', 'france': '🇫🇷',
      'ca': '🇨🇦', 'canada': '🇨🇦',
      'au': '🇦🇺', 'australia': '🇦🇺',
      'nl': '🇳🇱', 'netherlands': '🇳🇱',
      'in': '🇮🇳', 'india': '🇮🇳',
      'es': '🇪🇸', 'spain': '🇪🇸',
      'it': '🇮🇹', 'italy': '🇮🇹',
      'br': '🇧🇷', 'brazil': '🇧🇷',
      'pl': '🇵🇱', 'poland': '🇵🇱',
      'at': '🇦🇹', 'austria': '🇦🇹',
      'ch': '🇨🇭', 'switzerland': '🇨🇭',
      'ro': '🇷🇴', 'romania': '🇷🇴',
      'fb': '🇧🇷'
    };
    
    if (!countryName) return '🌍';
    const lowerName = countryName.toLowerCase();
    
    if (flags[lowerName]) {
        return flags[lowerName];
    }
    
    if (countryName.length === 2) {
         try {
            const codePoints = countryName.toUpperCase().split('').map(char => 127397 + char.charCodeAt());
            return String.fromCodePoint(...codePoints);
         } catch (e) {
            return '🌍';
         }
    }

    return '🌍';
  };

  const getIconForCategory = (categoryName) => {
    if (!categoryName) return '📁';
    const lowerCat = categoryName.toLowerCase();
    if (lowerCat.includes('it') || lowerCat.includes('software') || lowerCat.includes('dev')) return '💻';
    if (lowerCat.includes('data') || lowerCat.includes('analy')) return '📊';
    if (lowerCat.includes('design') || lowerCat.includes('ui') || lowerCat.includes('ux')) return '🎨';
    if (lowerCat.includes('market') || lowerCat.includes('seo')) return '📈';
    if (lowerCat.includes('sale') || lowerCat.includes('business')) return '💼';
    return '📁';
  };

  const getIconForLanguage = (languageName) => {
    if (!languageName) return '⌨️';
    const lowerLang = languageName.toLowerCase();
    if (lowerLang.includes('java') || lowerLang.includes('kotlin') || lowerLang.includes('scala')) return '☕';
    if (lowerLang.includes('python')) return '🐍';
    if (lowerLang.includes('javascript') || lowerLang.includes('js') || lowerLang.includes('node') || lowerLang.includes('typescript')) return '📜';
    if (lowerLang.includes('c#') || lowerLang.includes('.net') || lowerLang.includes('c++') || lowerLang.includes('c')) return '⚙️';
    return '⌨️';
  };

  return (
    <div className="dashboard-container">
      <div className="hero-section">
        
        <Link to="/admin" className="admin-link" title="Admin Tasks">
          ⚙️
        </Link>

        <h1 className="hero-title">Find Your Next Opportunity</h1>
        <p className="hero-subtitle">
          Explore over <strong>{stats.totalJobs}</strong> jobs from <strong>{stats.totalCompanies}</strong> companies.
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

        <div className="grid-container">
          {stats.jobsByCountry && stats.jobsByCountry.length > 0 && (
              <div className="stats-grid-section">
                  <h3 className="grid-title">Popular Locations</h3>
                  <div className="grid">
                      {stats.jobsByCountry.map((item, index) => (
                          <div 
                              key={`country-${index}`} 
                              className="grid-card"
                              onClick={() => handleFilterClick('country', item.country)}
                          >
                              <span>{getFlagForCountry(item.country)}</span>
                              <span className="grid-name">{item.country}</span>
                              <span className="grid-count">{item.count} jobs</span>
                          </div>
                      ))}
                  </div>
              </div>
          )}

          {stats.jobsByCategory && stats.jobsByCategory.length > 0 && (
              <div className="stats-grid-section">
                  <h3 className="grid-title">Popular Categories</h3>
                  <div className="grid">
                      {stats.jobsByCategory.map((item, index) => (
                          <div 
                              key={`category-${index}`} 
                              className="grid-card"
                              onClick={() => handleFilterClick('category', item.category)}
                          >
                              <span>{getIconForCategory(item.category)}</span>
                              <span className="grid-name">{item.category}</span>
                              <span className="grid-count">{item.count} jobs</span>
                          </div>
                      ))}
                  </div>
              </div>
          )}

          {stats.jobsByLanguage && stats.jobsByLanguage.length > 0 && (
              <div className="stats-grid-section">
                  <h3 className="grid-title">Popular Programming Languages</h3>
                  <div className="grid">
                      {stats.jobsByLanguage.map((item, index) => (
                          <div 
                              key={`language-${index}`} 
                              className="grid-card"
                              onClick={() => handleFilterClick('search', item.language)}
                          >
                              <span>{getIconForLanguage(item.language)}</span>
                              <span className="grid-name">{item.language}</span>
                              <span className="grid-count">{item.count} jobs</span>
                          </div>
                      ))}
                  </div>
              </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;