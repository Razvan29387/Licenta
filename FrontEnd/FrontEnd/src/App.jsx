import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import './styles/App.css'
import Navbar from './components/Navbar';
import DashboardPage from './pages/DashboardPage';
import JobsPage from './pages/JobsPage';
import JobDetailsPage from './pages/JobDetailsPage';
import AdminPage from './pages/AdminPage'; // Importă noua pagină

function App() {
  return (
    <Router>
      <Navbar />
      <div className="container">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="/jobs/:id" element={<JobDetailsPage />} />
          <Route path="/admin" element={<AdminPage />} /> {/* Adaugă noua rută */}
        </Routes>
      </div>
    </Router>
  )
}

export default App