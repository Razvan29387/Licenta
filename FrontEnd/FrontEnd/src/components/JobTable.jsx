import React from 'react';
import { Link } from 'react-router-dom';

const JobTable = ({ jobs }) => {
  if (!jobs || jobs.length === 0) {
    return <p>No jobs loaded yet.</p>;
  }

  return (
    <div className="job-table-container">
      <h2>Jobs List (Fetched from Database)</h2>
      <table border="1" cellPadding="10" style={{ width: '100%', borderCollapse: 'collapse', marginTop: '10px' }}>
        <thead>
          <tr>
            <th>Title</th>
            <th>Company</th>
            <th>Category</th>
            <th>Location</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => {
            // Logica de extragere a datelor:
            // 1. Încercăm structura API backend (job.company.name)
            // 2. Încercăm structura API brută Adzuna (job.company.display_name sau job.company) dacă e string
            let companyName = 'N/A';

            if (job.company) {
                if (typeof job.company === 'string') {
                    companyName = job.company;
                } else if (job.company.name) {
                    companyName = job.company.name;
                } else if (job.company.display_name) {
                    companyName = job.company.display_name;
                }
            }
            
            // Asigurăm-ne că location e string
            const location = typeof job.location === 'string' 
                ? job.location 
                : (job.location?.display_name || 'N/A');

            // Asigurăm-ne că category e string
            const category = typeof job.category === 'string' 
                ? job.category 
                : (job.category?.label || 'N/A');

            const applyUrl = job.url || job.redirect_url || '#';

            return (
            <tr key={job.id || Math.random()}>
              <td>
                <Link to={`/jobs/${job.id}`} style={{ textDecoration: 'none', color: '#007bff', fontWeight: 'bold' }}>
                  {job.title}
                </Link>
              </td>
              <td>{companyName}</td>
              <td>{category}</td>
              <td>{location}</td>
              <td>
                <a href={applyUrl} target="_blank" rel="noopener noreferrer">
                  Apply
                </a>
              </td>
            </tr>
          );
          })}
        </tbody>
      </table>
    </div>
  );
};

export default JobTable;