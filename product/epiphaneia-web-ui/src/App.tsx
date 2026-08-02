import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SetupWizard from './pages/SetupWizard';
import DiagnosisWorkspace from './pages/DiagnosisWorkspace';
import ReportView from './pages/ReportView';
import HistoryPage from './pages/HistoryPage';
import SettingsPage from './pages/SettingsPage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        {/* ProtectedRoute double-duty: blocks unauthenticated access AND its GET /auth/me
            triggers the CSRF filter to issue the XSRF-TOKEN cookie before the first POST */}
        <Route path="/setup" element={<ProtectedRoute><SetupWizard /></ProtectedRoute>} />
        <Route element={<Layout />}>
          <Route path="/workspace" element={<ProtectedRoute><DiagnosisWorkspace /></ProtectedRoute>} />
          <Route path="/report/:id" element={<ProtectedRoute><ReportView /></ProtectedRoute>} />
          <Route path="/history" element={<ProtectedRoute><HistoryPage /></ProtectedRoute>} />
          <Route path="/settings" element={<ProtectedRoute><SettingsPage /></ProtectedRoute>} />
        </Route>
        <Route path="*" element={<Navigate to="/workspace" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
