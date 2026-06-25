import React from 'react';
import { Link } from 'react-router-dom';

const JobCard = ({ job }) => {

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
    };

    // Funcție reutilizată pentru steaguri
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

    const getShortDescription = (description) => {
        if (!description) return "No description available.";
        const cleanText = description.replace(/<[^>]*>?/gm, '');
        return cleanText.length > 250 ? cleanText.substring(0, 250) + "..." : cleanText;
    };

    const styles = {
        card: { backgroundColor: 'white', borderRadius: '12px', padding: '25px', boxShadow: '0 2px 8px rgba(0,0,0,0.08)', borderLeft: '5px solid #007bff', transition: 'transform 0.2s', display: 'flex', flexDirection: 'column' },
        cardTitle: { fontSize: '20px', fontWeight: 'bold', margin: '0 0 10px 0', color: '#2c3e50' },
        metaContainer: { display: 'flex', flexWrap: 'wrap', gap: '10px', marginBottom: '15px', fontSize: '14px', color: '#555' },
        description: { color: '#444', fontSize: '15px', lineHeight: '1.6', marginBottom: '20px', flexGrow: 1 },
        tag: { padding: '5px 12px', borderRadius: '20px', fontSize: '12px', fontWeight: '600', display: 'inline-flex', alignItems: 'center', gap: '5px' },
        applyBtn: { alignSelf: 'flex-start', backgroundColor: '#28a745', color: 'white', padding: '10px 25px', borderRadius: '6px', textDecoration: 'none', fontWeight: 'bold', transition: 'background-color 0.2s' },
    };

    const country = getCountryName(job);

    return (
        <div style={styles.card} className="job-card-hover">
            <h3 style={styles.cardTitle}>
                <Link to={`/jobs/${job.id}`} style={{textDecoration: 'none', color: 'inherit'}}>{job.title || 'Untitled Job'}</Link>
            </h3>
            <div style={styles.metaContainer}>
                <span style={{...styles.tag, backgroundColor: '#f8f9fa', border: '1px solid #ddd'}}>🏢 {getCompanyName(job)}</span>
                <span style={{...styles.tag, backgroundColor: '#f8f9fa', border: '1px solid #ddd'}}>📍 {getLocationName(job)}</span>
                <span style={{...styles.tag, backgroundColor: '#e3f2fd', color: '#0d47a1'}}>📁 {getCategoryName(job)}</span>
                <span style={{...styles.tag, backgroundColor: '#fff3cd', color: '#856404'}}>{getFlagForCountry(country)} {country}</span>
            </div>
            <p style={styles.description}>{getShortDescription(job.description)}</p>
            <a href={job.url || '#'} target="_blank" rel="noopener noreferrer" style={styles.applyBtn}>
                {job.url ? 'Apply Externally ↗' : 'Apply on Platform'}
            </a>
        </div>
    );
};

export default JobCard;