import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import '../styles/CompanySearchPage.css';

const CompanySearchPage = () => {
    const [companyName, setCompanyName] = useState('');
    const navigate = useNavigate();
    const [error, setError] = useState('');

    const handleSearch = async () => {
        if (!companyName.trim()) {
            setError('Please enter a company name.');
            return;
        }
        setError('');
        try {
            // We use a POST request to find or create the company
            const response = await fetch('/api/companies/find-or-create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: companyName.trim() })
            });

            if (response.ok) {
                const company = await response.json();
                navigate(`/company/${company.id}`);
            } else {
                throw new Error('Could not find or create company.');
            }
        } catch (err) {
            setError(err.message);
        }
    };

    return (
        <div className="company-search-container">
            <div className="search-box">
                <h1>Company Dashboard</h1>
                <p>Enter your company name to manage your job postings and applicants.</p>
                <input
                    type="text"
                    value={companyName}
                    onChange={(e) => setCompanyName(e.target.value)}
                    placeholder="Your Company Name"
                />
                <button onClick={handleSearch}>Access Dashboard</button>
                {error && <p className="error-message">{error}</p>}
            </div>
        </div>
    );
};

export default CompanySearchPage;
