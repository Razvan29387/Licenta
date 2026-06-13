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
    jobsBySkill: [], // Added skills
    jobsByOccupation: [] // Added occupations
  });
  const [searchTerm, setSearchTerm] = useState('');
  const [skillTerm, setSkillTerm] = useState('');
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
              jobsBySkill: data.jobsBySkill || [], // Added skills
              jobsByOccupation: data.jobsByOccupation || [] // Added occupations
          });
        }
      } catch (error) {
        console.error("Failed to fetch stats:", error);
      }
    };

    fetchStats();
  }, []);

  const handleSearch = () => {
    // Combine search and skill into one search term
    let combinedSearch = searchTerm.trim();
    if (skillTerm.trim()) {
      if (combinedSearch) {
        combinedSearch = combinedSearch + " " + skillTerm.trim();
      } else {
        combinedSearch = skillTerm.trim();
      }
    }
    navigate('/jobs', {
      state: {
        search: combinedSearch
      }
    });
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

   const getIconForSkill = (skillName) => {
     if (!skillName) return '🔧';
     const lowerSkill = skillName.toLowerCase();
     if (lowerSkill.includes('java') || lowerSkill.includes('kotlin') || lowerSkill.includes('scala')) return '☕';
     if (lowerSkill.includes('python')) return '🐍';
     if (lowerSkill.includes('javascript') || lowerSkill.includes('js') || lowerSkill.includes('node') || lowerSkill.includes('typescript')) return '📜';
     if (lowerSkill.includes('c#') || lowerSkill.includes('.net') || lowerSkill.includes('c++') || lowerSkill.includes('c')) return '⚙️';
     if (lowerSkill.includes('react') || lowerSkill.includes('angular') || lowerSkill.includes('vue')) return '⚛️';
     if (lowerSkill.includes('aws') || lowerSkill.includes('azure') || lowerSkill.includes('gcp')) return '☁️';
     if (lowerSkill.includes('docker') || lowerSkill.includes('kubernetes')) return '🐳';
     if (lowerSkill.includes('sql')) return '🗃️';
     return '🔧';
   };

   const getIconForOccupation = (occupationName) => {
     if (!occupationName) return '👔';
     const lowerOcc = occupationName.toLowerCase();
     if (lowerOcc.includes('developer') || lowerOcc.includes('programmer') || lowerOcc.includes('engineer')) return '👨‍💻';
     if (lowerOcc.includes('architect')) return '🏗️';
     if (lowerOcc.includes('manager') || lowerOcc.includes('lead')) return '👨‍💼';
     if (lowerOcc.includes('designer') || lowerOcc.includes('ux') || lowerOcc.includes('ui')) return '🎨';
     if (lowerOcc.includes('analyst') || lowerOcc.includes('data')) return '📊';
     if (lowerOcc.includes('tester') || lowerOcc.includes('qa')) return '🧪';
     if (lowerOcc.includes('devops') || lowerOcc.includes('sre')) return '⚙️';
     if (lowerOcc.includes('security')) return '🔒';
     if (lowerOcc.includes('admin') || lowerOcc.includes('support')) return '🛠️';
     return '👔';
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
           <input 
              type="text" 
              className="search-input"
              placeholder="Filter by skill (e.g., Python, React, Java)" 
              value={skillTerm}
              onChange={(e) => setSkillTerm(e.target.value)}
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

           {stats.jobsBySkill && stats.jobsBySkill.length > 0 && (
               <div className="stats-grid-section">
                   <h3 className="grid-title">Top Skills</h3>
                   <div className="grid">
                       {stats.jobsBySkill.map((item, index) => (
                           <div
                               key={`skill-${index}`}
                               className="grid-card"
                               onClick={() => handleFilterClick('search', item.skill)}
                           >
                               <span>{getIconForSkill(item.skill)}</span>
                               <span className="grid-name">{item.skill}</span>
                               <span className="grid-count">{item.count} jobs</span>
                           </div>
                       ))}
                   </div>
               </div>
           )}

           {stats.jobsByOccupation && stats.jobsByOccupation.length > 0 && (
               <div className="stats-grid-section">
                   <h3 className="grid-title">Popular Occupations</h3>
                   <div className="grid">
                       {stats.jobsByOccupation.map((item, index) => (
                           <div
                               key={`occupation-${index}`}
                               className="grid-card"
                               onClick={() => handleFilterClick('occupation', item.occupation)}
                           >
                               <span>{getIconForOccupation(item.occupation)}</span>
                               <span className="grid-name">{item.occupation}</span>
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