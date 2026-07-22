import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout';
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
        <Route path="/setup" element={<SetupWizard />} />
        <Route element={<Layout />}>
          <Route path="/workspace" element={<DiagnosisWorkspace />} />
          <Route path="/report/:id" element={<ReportView />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/workspace" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
