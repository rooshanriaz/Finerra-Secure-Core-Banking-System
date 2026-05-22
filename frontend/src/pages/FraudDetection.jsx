import { useState, useEffect, useRef } from 'react';
import {
  AlertTriangle, Shield, Search, Eye, CheckCircle2, XCircle,
  Clock, TrendingUp, Activity, Filter, AlertCircle, ArrowUpRight,
  BarChart3, PieChart as PieChartIcon, Zap, Lock, SlidersHorizontal, Loader2
} from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
} from 'recharts';
import fraudService from '../services/fraudService';
import { useAuth, hasSuperAdminAccess } from '../context/AuthContext';
import { PageShell } from '../components/layout/PageShell';
import { formatPkr } from '../lib/formatCurrency';

const COLORS = ['#6366F1', '#F59E0B', '#EF4444', '#3B82F6', '#22C55E'];

const riskLevelColors = {
  CRITICAL: 'bg-red-100 text-red-700 border-red-200',
  HIGH: 'bg-orange-100 text-orange-700 border-orange-200',
  MEDIUM: 'bg-amber-100 text-amber-700 border-amber-200',
  LOW: 'bg-emerald-100 text-emerald-700 border-emerald-200',
};

const statusColors = {
  OPEN: 'badge-warning',
  INVESTIGATING: 'badge-info',
  RESOLVED: 'badge-success',
  FALSE_POSITIVE: 'bg-gray-100 text-gray-700',
  ESCALATED: 'badge-danger',
  BLOCKED: 'badge-purple',
};

/** Must match fraud-detection-service RiskThresholdInitializer + RiskScoringService */
const THRESHOLD_HIGH_VALUE_PKR = 'high_value_amount_pkr';
const THRESHOLD_VELOCITY = 'velocity_tx_per_hour';
const THRESHOLD_GEO = 'geo_anomaly_score';
const EXCLUDED_ALERT_TYPES = new Set(['LOAN_APPLICATION']);

export default function FraudDetection() {
  const { user } = useAuth();
  const canEditThresholds = hasSuperAdminAccess(user);
  const canManageAlerts = hasSuperAdminAccess(user);
  const [alerts, setAlerts] = useState([]);
  const [stats, setStats] = useState({ total: 0, open: 0, investigating: 0, resolved: 0, false_positive: 0, critical: 0, high: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionLoading, setActionLoading] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [thresholdSaving, setThresholdSaving] = useState(false);
  const [thresholdError, setThresholdError] = useState(null);
  const thresholdsLoadedRef = useRef(false);

  const [selectedAlert, setSelectedAlert] = useState(null);
  const [filterRiskLevel, setFilterRiskLevel] = useState('All');
  const [amountThreshold, setAmountThreshold] = useState(50000);
  const [velocityThreshold, setVelocityThreshold] = useState(3);
  const [geoThreshold, setGeoThreshold] = useState(80);

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [alertsRes, statsRes, thrRes] = await Promise.all([
        fraudService.getAlerts(),
        fraudService.getStats(),
        fraudService.getThresholds().catch(() => ({ success: false, data: [] })),
      ]);
      const allAlerts = alertsRes.success ? (alertsRes.data || []) : [];
      const filtered = allAlerts.filter((a) => !EXCLUDED_ALERT_TYPES.has(String(a.transactionType || '').toUpperCase()));
      setAlerts(filtered);
      if (statsRes.success) setStats(statsRes.data);
      if (thrRes.success && Array.isArray(thrRes.data)) {
        const byName = Object.fromEntries(thrRes.data.map((t) => [t.thresholdName, t.value]));
        if (byName[THRESHOLD_HIGH_VALUE_PKR] != null) setAmountThreshold(byName[THRESHOLD_HIGH_VALUE_PKR]);
        if (byName[THRESHOLD_VELOCITY] != null) setVelocityThreshold(byName[THRESHOLD_VELOCITY]);
        if (byName[THRESHOLD_GEO] != null) setGeoThreshold(byName[THRESHOLD_GEO]);
      }
      thresholdsLoadedRef.current = true;
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data?.error || err.message;
      setError(msg || 'Failed to load fraud data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const saveThresholds = async () => {
    if (!canEditThresholds || !thresholdsLoadedRef.current) return;
    try {
      setThresholdSaving(true);
      setThresholdError(null);
      await Promise.all([
        fraudService.updateThreshold(THRESHOLD_HIGH_VALUE_PKR, amountThreshold, 'PKR amount above which risk is boosted'),
        fraudService.updateThreshold(THRESHOLD_VELOCITY, velocityThreshold, 'Transactions per hour before velocity boost'),
        fraudService.updateThreshold(THRESHOLD_GEO, geoThreshold, 'Geo anomaly score (reserved for future geo-based rules)'),
      ]);
    } catch (err) {
      setThresholdError(err.response?.data?.message || err.message || 'Failed to save thresholds');
    } finally {
      setThresholdSaving(false);
    }
  };

  const handleStatusUpdate = async (alertId, status, note) => {
    if (!canManageAlerts) {
      setActionError('Only Super Admin can update alert status.');
      return;
    }
    try {
      setActionLoading(alertId);
      setActionError(null);
      await fraudService.updateAlertStatus(alertId, status, note);
      await fetchData();
      setSelectedAlert(null);
    } catch (err) {
      const message = err.response?.data?.message || err.message || 'Failed to update alert status';
      setActionError(message);
    } finally {
      setActionLoading(null);
    }
  };

  const filteredAlerts = alerts.filter(
    a => filterRiskLevel === 'All' || a.riskLevel === filterRiskLevel
  );

  const displayStats = {
    total: alerts.length,
    critical: alerts.filter((a) => String(a.riskLevel || '').toUpperCase() === 'CRITICAL').length,
    high: alerts.filter((a) => String(a.riskLevel || '').toUpperCase() === 'HIGH').length,
    open: alerts.filter((a) => String(a.status || '').toUpperCase() === 'OPEN').length,
    investigating: alerts.filter((a) => String(a.status || '').toUpperCase() === 'INVESTIGATING').length,
    resolved: alerts.filter((a) => String(a.status || '').toUpperCase() === 'RESOLVED').length,
  };

  const categoryData = alerts.reduce((acc, a) => {
    const type = a.transactionType || 'Unknown';
    const existing = acc.find(x => x.name === type);
    if (existing) existing.value++;
    else acc.push({ name: type, value: 1 });
    return acc;
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader2 className="w-8 h-8 text-finnera-500 animate-spin" />
        <span className="ml-3 text-gray-500">Loading fraud detection data...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-96 gap-4">
        <AlertTriangle className="w-12 h-12 text-red-400" />
        <p className="text-red-600 font-medium">{error}</p>
        <button onClick={fetchData} className="btn-primary rounded-lg px-6 py-2 text-sm">Retry</button>
      </div>
    );
  }

  return (
    <PageShell
      title="Fraud detection & monitoring"
      subtitle="Real-time anomaly detection, queue triage, and threshold tuning. Connectors feed the fraud service; this view refreshes on demand."
    >

      {/* Alert Banner */}
      {displayStats.critical > 0 && (
        <div className="flex items-start gap-3 bg-red-50 border border-red-200 rounded-xl p-4">
          <AlertTriangle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0 animate-pulse" />
          <div>
            <p className="text-sm font-semibold text-red-800">
              {displayStats.critical} Critical Alert{displayStats.critical > 1 ? 's' : ''} Requiring Immediate Attention
            </p>
            <p className="text-xs text-red-600 mt-0.5">
              AI-powered anomaly detection has flagged suspicious patterns. Review below for details.
            </p>
          </div>
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="card bg-red-50 border-red-100 border">
          <p className="text-sm text-red-600 font-medium">Total Alerts</p>
          <div className="flex items-center justify-between mt-2">
            <p className="text-3xl font-bold text-gray-900">{displayStats.total}</p>
            <AlertTriangle className="w-6 h-6 text-red-400" />
          </div>
        </div>
        <div className="card bg-orange-50 border-orange-100 border">
          <p className="text-sm text-orange-600 font-medium">Critical Severity</p>
          <div className="flex items-center justify-between mt-2">
            <p className="text-3xl font-bold text-gray-900">{displayStats.critical}</p>
            <AlertCircle className="w-6 h-6 text-orange-400" />
          </div>
        </div>
        <div className="card bg-amber-50 border-amber-100 border">
          <p className="text-sm text-amber-600 font-medium">Open / Investigating</p>
          <div className="flex items-center justify-between mt-2">
            <p className="text-3xl font-bold text-gray-900">{displayStats.open + displayStats.investigating}</p>
            <Eye className="w-6 h-6 text-amber-400" />
          </div>
        </div>
        <div className="card bg-purple-50 border-purple-100 border">
          <p className="text-sm text-purple-600 font-medium">Resolved</p>
          <div className="flex items-center justify-between mt-2">
            <p className="text-3xl font-bold text-gray-900">{displayStats.resolved}</p>
            <CheckCircle2 className="w-6 h-6 text-purple-400" />
          </div>
        </div>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Category Distribution */}
        <div className="card lg:col-span-1">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-semibold text-gray-900">Alert Categories</h3>
              <p className="text-sm text-gray-500">Distribution by transaction type</p>
            </div>
            <PieChartIcon className="w-5 h-5 text-gray-400" />
          </div>
          <ResponsiveContainer width="100%" height={250}>
            <PieChart>
              <Pie
                data={categoryData}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={90}
                fill="#8884d8"
                paddingAngle={3}
                dataKey="value"
              >
                {categoryData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }} />
              <Legend iconType="circle" iconSize={8} formatter={(value) => <span className="text-sm text-gray-600">{value}</span>} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Risk Level Distribution */}
        <div className="card lg:col-span-1">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-base font-semibold text-gray-900">Risk Distribution</h3>
              <p className="text-sm text-gray-500">By risk level</p>
            </div>
            <BarChart3 className="w-5 h-5 text-gray-400" />
          </div>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={[
              { name: 'Critical', count: displayStats.critical },
              { name: 'High', count: displayStats.high },
              { name: 'Open', count: displayStats.open },
              { name: 'Resolved', count: displayStats.resolved },
            ]}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="name" tick={{ fontSize: 12 }} stroke="#94a3b8" />
              <YAxis tick={{ fontSize: 12 }} stroke="#94a3b8" />
              <Tooltip contentStyle={{ borderRadius: '12px', border: '1px solid #e2e8f0' }} />
              <Bar dataKey="count" fill="#6366F1" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Threshold Configuration */}
        <div className="card lg:col-span-1">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
            <div className="flex items-center gap-2">
              <SlidersHorizontal className="w-5 h-5 text-finnera-600" />
              <h3 className="text-base font-semibold text-gray-900">Risk Thresholds</h3>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="badge-info text-xs">Fraud service API</span>
              {canEditThresholds ? (
                <button
                  type="button"
                  onClick={saveThresholds}
                  disabled={thresholdSaving}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-finnera-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-finnera-700 disabled:opacity-50"
                >
                  {thresholdSaving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Lock className="h-3.5 w-3.5" />}
                  Save thresholds
                </button>
              ) : (
                <span className="text-[11px] text-gray-500">View only — Super Admin can edit</span>
              )}
            </div>
          </div>

          {thresholdError && (
            <p className="mb-3 text-xs text-red-600">{thresholdError}</p>
          )}

          <div className="space-y-4">
            <ThresholdSlider
              label="High-value transaction amount"
              description="PKR amount above which the fraud service adds risk on top of the ML score."
              unit="PKR"
              min={10000}
              max={100000}
              step={5000}
              value={amountThreshold}
              onChange={setAmountThreshold}
              disabled={!canEditThresholds}
            />
            <ThresholdSlider
              label="Velocity threshold (transactions / hour)"
              description="When recent hourly activity exceeds this count, additional risk is applied."
              unit="tx/h"
              min={1}
              max={10}
              step={1}
              value={velocityThreshold}
              onChange={setVelocityThreshold}
              disabled={!canEditThresholds}
            />
            <ThresholdSlider
              label="Geo-anomaly score"
              description="Reserved for geo-based rules when location signals are integrated."
              unit="%"
              min={50}
              max={100}
              step={5}
              value={geoThreshold}
              onChange={setGeoThreshold}
              disabled={!canEditThresholds}
            />
          </div>

          <p className="text-[11px] text-gray-500 mt-4 pt-3 border-t border-gray-100">
            Values load from and save to the fraud detection service (<code className="text-[10px]">GET/PUT /api/v1/fraud/thresholds</code>).
            ML block/flag cutoffs (<code className="text-[10px]">block</code>, <code className="text-[10px]">flag</code>) are seeded in the database and used together with these rule boosts during transaction scoring.
          </p>
        </div>
      </div>

      {/* Alerts Table */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Fraud Alerts</h3>
            <p className="text-sm text-gray-500">Click on an alert to view details and take action</p>
          </div>
          <div className="flex items-center gap-3">
            <select
              value={filterRiskLevel}
              onChange={(e) => setFilterRiskLevel(e.target.value)}
              className="px-4 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 bg-white focus:outline-none focus:ring-2 focus:ring-finnera-500"
            >
              <option value="All">All Risk Levels</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>
        </div>

        {filteredAlerts.length === 0 ? (
          <div className="text-center py-12 text-gray-400">
            <Shield className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p className="font-medium">No alerts found</p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredAlerts.map((alert) => (
              <div
                key={alert.alertId}
                className={`p-4 rounded-xl border cursor-pointer transition-all hover:shadow-md ${
                  selectedAlert?.alertId === alert.alertId
                    ? 'border-finnera-300 bg-finnera-50 shadow-sm'
                    : 'border-gray-100 bg-white hover:border-gray-200'
                }`}
                onClick={() => setSelectedAlert(selectedAlert?.alertId === alert.alertId ? null : alert)}
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3">
                    <div className={`p-2 rounded-lg border ${riskLevelColors[alert.riskLevel] || 'bg-gray-100 text-gray-700 border-gray-200'}`}>
                      <AlertTriangle className="w-4 h-4" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-semibold text-gray-900">{alert.transactionType || 'Transaction'} Alert</p>
                        <span className={`badge ${
                          alert.riskLevel === 'CRITICAL' ? 'badge-danger' :
                          alert.riskLevel === 'HIGH' ? 'bg-orange-100 text-orange-700' :
                          alert.riskLevel === 'MEDIUM' ? 'badge-warning' : 'badge-success'
                        }`}>
                          {alert.riskLevel}
                        </span>
                      </div>
                      <p className="text-xs text-gray-500 mt-0.5">
                        Account {alert.accountId} &middot; {new Date(alert.createdAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    {alert.transactionAmount > 0 && (
                      <p className="text-sm font-bold text-gray-900">{formatPkr(alert.transactionAmount)}</p>
                    )}
                    <span className={`badge ${statusColors[alert.status] || 'badge-info'}`}>
                      {alert.status?.replace('_', ' ')}
                    </span>
                  </div>
                </div>

                {/* Expanded Details */}
                {selectedAlert?.alertId === alert.alertId && (
                  <div className="mt-4 pt-4 border-t border-gray-100 animate-fadeIn">
                    <div className="mb-4">
                      <p className="text-xs font-semibold text-gray-500 mb-1">Risk Factors</p>
                      <div className="flex flex-wrap gap-2">
                        {(alert.riskFactors || []).map((factor, i) => (
                          <span key={i} className="px-2 py-1 bg-red-50 text-red-700 rounded-md text-xs">{factor}</span>
                        ))}
                      </div>
                      {alert.recommendation && (
                        <p className="text-sm text-gray-700 mt-2"><strong>Recommendation:</strong> {alert.recommendation}</p>
                      )}
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <div>
                        <p className="text-xs text-gray-500">Alert ID</p>
                        <p className="text-sm font-mono font-semibold text-gray-900">{alert.alertId}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500">Transaction ID</p>
                        <p className="text-sm font-mono font-semibold text-gray-900">{alert.transactionId}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500">Risk Score (0-1)</p>
                        <p className="text-sm font-semibold text-gray-900">{Number(alert.riskScore ?? 0).toFixed(2)}</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-500">Initiated By</p>
                        <p className="text-sm font-semibold text-gray-900">{alert.initiatedBy || 'System'}</p>
                      </div>
                    </div>
                    {actionError && (
                      <p className="mt-3 text-xs text-red-600">{actionError}</p>
                    )}
                    <div className="flex gap-3 mt-4">
                      <button
                        disabled={actionLoading === alert.alertId || !canManageAlerts || alert.status === 'INVESTIGATING'}
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(alert.alertId, 'INVESTIGATING', 'Opened investigation'); }}
                        className="btn-primary text-sm py-2 px-4 rounded-lg flex items-center gap-2 hover:opacity-90 transition-opacity disabled:opacity-50"
                        title={canManageAlerts ? '' : 'Only Super Admin can update status'}
                      >
                        {actionLoading === alert.alertId ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
                        Investigate
                      </button>
                      <button
                        disabled={actionLoading === alert.alertId || !canManageAlerts || alert.status === 'RESOLVED'}
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(alert.alertId, 'RESOLVED', 'Alert resolved'); }}
                        className="px-4 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 disabled:opacity-50"
                        title={canManageAlerts ? '' : 'Only Super Admin can update status'}
                      >
                        <CheckCircle2 className="w-4 h-4" />
                        Mark Resolved
                      </button>
                      <button
                        disabled={actionLoading === alert.alertId || !canManageAlerts || alert.status === 'FALSE_POSITIVE'}
                        onClick={(e) => { e.stopPropagation(); handleStatusUpdate(alert.alertId, 'FALSE_POSITIVE', 'Marked as false positive'); }}
                        className="px-4 py-2 bg-gray-50 hover:bg-gray-100 text-gray-700 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 disabled:opacity-50"
                        title={canManageAlerts ? '' : 'Only Super Admin can update status'}
                      >
                        <XCircle className="w-4 h-4" />
                        False Positive
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Real-time Monitoring Notice */}
      <div className="card bg-gray-50 border-dashed border-gray-200">
        <div className="flex items-center gap-3">
          <div className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse" />
          <p className="text-sm text-gray-600">
            <strong>Real-time monitoring active.</strong> AI-powered anomaly detection is continuously scanning
            transaction patterns. All alerts and threshold changes are logged for audit and compliance, and
            transactions exceeding configured risk thresholds are blocked by design.
          </p>
        </div>
      </div>
    </PageShell>
  );
}

function ThresholdSlider({ label, description, unit, min, max, step, value, onChange, disabled }) {
  return (
    <div className={disabled ? 'opacity-75' : ''}>
      <div className="flex items-center justify-between mb-1.5">
        <p className="text-sm font-medium text-gray-800">{label}</p>
        <span className="text-xs font-semibold text-finnera-700">
          {unit === 'PKR'
            ? `PKR ${value.toLocaleString('en-PK')}`
            : `${value}${unit}`}
        </span>
      </div>
      <p className="text-xs text-gray-500 mb-2">{description}</p>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-finnera-500 disabled:cursor-not-allowed"
      />
    </div>
  );
}
