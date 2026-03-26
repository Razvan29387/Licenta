import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import './App.css'
import Navbar from './components/Navbar';
import FirmePage from './pages/FirmePage';
import JobsPage from './pages/JobsPage';
import DashboardPage from './pages/DashboardPage';
import JobDetailsPage from './pages/JobDetailsPage';
import CompanyProfilePage from './pages/CompanyProfilePage'; // Adăugat!

function App() {
  return (
    <Router>
      <Navbar />
      <div className="container">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/firme" element={<FirmePage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/jobs/:id" element={<JobDetailsPage />} />
          {/* RUTA NOUA */}
          <Route path="/company-dashboard" element={<CompanyProfilePage />} />
        </Routes>
      </div>
    </Router>
  )
}

export default App