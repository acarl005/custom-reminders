import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const _channel = MethodChannel('dev.andy.custom_reminders/native');

void main() {
  runApp(const ReminderApp());
}

class ReminderApp extends StatelessWidget {
  const ReminderApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Squat Reminders',
      theme: ThemeData(colorSchemeSeed: Colors.deepPurple, useMaterial3: true),
      darkTheme: ThemeData(
        colorSchemeSeed: Colors.deepPurple,
        useMaterial3: true,
        brightness: Brightness.dark,
      ),
      themeMode: ThemeMode.system,
      home: const HomePage(),
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  bool _active = true; // inverse of "paused"
  bool _soundEnabled = true;
  bool _canScheduleExactAlarms = true;
  bool _notificationsEnabled = true;
  bool _ignoringBatteryOptimizations = true;
  bool _skipIfActiveEnabled = true;
  bool _healthConnectAvailable = true;
  bool _stepsPermissionGranted = true;
  bool _lastSkippedForActivity = false;
  int _lastSkippedStepCount = 0;
  bool _loaded = false;
  DateTime? _snoozedUntil;
  int? _currentIntervalSteps;
  int _activityStepThreshold = 200;
  Timer? _ticker;
  Timer? _stepsTicker;

  static const List<int> _reminderHours = [
    10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
    _stepsTicker =
        Timer.periodic(const Duration(seconds: 10), (_) => _tickSteps());
    _tickSteps();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ticker?.cancel();
    _stepsTicker?.cancel();
    super.dispose();
  }

  Future<void> _tickSteps() async {
    if (!_skipIfActiveEnabled || !_canUseSkipIfActive) {
      if (_currentIntervalSteps != null && mounted) {
        setState(() => _currentIntervalSteps = null);
      }
      return;
    }
    final steps = await _channel.invokeMethod<int>('getCurrentIntervalSteps');
    if (!mounted) return;
    setState(() => _currentIntervalSteps = steps);
  }

  Future<void> _tick() async {
    final snoozedUntilMillis =
        await _channel.invokeMethod<int>('getSnoozedUntil') ?? 0;
    final lastSkipped =
        await _channel.invokeMethod<bool>('wasLastReminderSkippedForActivity') ??
            false;
    final lastSkippedStepCount =
        await _channel.invokeMethod<int>('getLastSkippedStepCount') ?? 0;
    if (!mounted) return;
    setState(() {
      _snoozedUntil = snoozedUntilMillis > 0
          ? DateTime.fromMillisecondsSinceEpoch(snoozedUntilMillis)
          : null;
      _lastSkippedForActivity = lastSkipped;
      _lastSkippedStepCount = lastSkippedStepCount;
    });
  }

  DateTime? _nextReminderTime() {
    if (!_active) return null;
    final now = DateTime.now();
    for (final hour in _reminderHours) {
      final candidate = DateTime(now.year, now.month, now.day, hour, 55);
      if (candidate.isAfter(now)) return candidate;
    }
    final tomorrow = now.add(const Duration(days: 1));
    return DateTime(tomorrow.year, tomorrow.month, tomorrow.day, _reminderHours.first, 55);
  }

  static String _formatClock(DateTime dt) {
    final h12 = dt.hour % 12 == 0 ? 12 : dt.hour % 12;
    final minute = dt.minute.toString().padLeft(2, '0');
    final amPm = dt.hour < 12 ? 'AM' : 'PM';
    return '$h12:$minute $amPm';
  }

  static String _formatCountdown(Duration d) {
    if (d.isNegative) return 'any moment now';
    final hours = d.inHours;
    final minutes = d.inMinutes % 60;
    final seconds = d.inSeconds % 60;
    if (hours > 0) return '${hours}h ${minutes}m';
    if (minutes > 0) return '${minutes}m ${seconds}s';
    return '${seconds}s';
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refreshPermissionStatus();
    }
  }

  Future<void> _init() async {
    await _channel.invokeMethod('scheduleAllAlarms');
    final paused = await _channel.invokeMethod<bool>('getPaused') ?? false;
    final soundEnabled =
        await _channel.invokeMethod<bool>('getSoundEnabled') ?? true;
    final skipIfActiveEnabled =
        await _channel.invokeMethod<bool>('getSkipIfActiveEnabled') ?? true;
    final activityStepThreshold =
        await _channel.invokeMethod<int>('getActivityStepThreshold') ?? 200;
    setState(() {
      _active = !paused;
      _soundEnabled = soundEnabled;
      _skipIfActiveEnabled = skipIfActiveEnabled;
      _activityStepThreshold = activityStepThreshold;
      _loaded = true;
    });
    await _refreshPermissionStatus();
  }

  Future<void> _refreshPermissionStatus() async {
    final canSchedule =
        await _channel.invokeMethod<bool>('canScheduleExactAlarms') ?? true;
    final notificationsEnabled =
        await _channel.invokeMethod<bool>('areNotificationsEnabled') ?? true;
    final ignoringBatteryOptimizations =
        await _channel.invokeMethod<bool>('isIgnoringBatteryOptimizations') ??
            true;
    final healthConnectAvailable =
        await _channel.invokeMethod<bool>('isHealthConnectAvailable') ?? false;
    final stepsPermissionGranted =
        await _channel.invokeMethod<bool>('hasStepsPermission') ?? false;
    if (!mounted) return;
    setState(() {
      _canScheduleExactAlarms = canSchedule;
      _notificationsEnabled = notificationsEnabled;
      _ignoringBatteryOptimizations = ignoringBatteryOptimizations;
      _healthConnectAvailable = healthConnectAvailable;
      _stepsPermissionGranted = stepsPermissionGranted;
    });
  }

  Future<void> _setActive(bool value) async {
    setState(() => _active = value);
    await _channel.invokeMethod('setPaused', {'value': !value});
  }

  Future<void> _setSoundEnabled(bool value) async {
    setState(() => _soundEnabled = value);
    await _channel.invokeMethod('setSoundEnabled', {'value': value});
  }

  Future<void> _setSkipIfActiveEnabled(bool value) async {
    setState(() {
      _skipIfActiveEnabled = value;
      if (!value) _currentIntervalSteps = null;
    });
    await _channel.invokeMethod('setSkipIfActiveEnabled', {'value': value});
  }

  bool get _canUseSkipIfActive => _healthConnectAvailable && _stepsPermissionGranted;

  /// Picks a shade of [base] appropriate for the current light/dark theme,
  /// so card backgrounds stay legible in both modes.
  Color _tone(MaterialColor base, {int light = 50, int dark = 900}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return (isDark ? base[dark] : base[light])!;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Squat Reminders')),
      body: !_loaded
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(16, 16, 16, 0),
                  child: Text(
                    'Fires every hour, 5 minutes before the hour, '
                    'from 10:55 AM to 10:55 PM.',
                    style: TextStyle(color: Colors.grey),
                  ),
                ),
                if (!_canScheduleExactAlarms)
                  _permissionBanner(
                    'Exact alarm permission is required for reminders to '
                    'fire on time.',
                    'Allow',
                    () => _channel.invokeMethod('requestScheduleExactAlarmPermission'),
                  ),
                if (!_notificationsEnabled)
                  _permissionBanner(
                    'Notifications are disabled, so reminders will not appear.',
                    'Enable',
                    () => _channel.invokeMethod('requestNotificationPermission'),
                  ),
                if (!_ignoringBatteryOptimizations)
                  _permissionBanner(
                    'Battery optimization may delay or block reminders while '
                    "the app isn't open.",
                    'Fix',
                    () => _channel.invokeMethod('requestIgnoreBatteryOptimizations'),
                  ),
                if (_skipIfActiveEnabled && !_healthConnectAvailable)
                  Card(
                    margin: const EdgeInsets.all(12),
                    color: _tone(Colors.grey, light: 200, dark: 800),
                    child: const Padding(
                      padding: EdgeInsets.all(12),
                      child: Text(
                        "Health Connect isn't available on this device, so "
                        '"Skip if already active" has no effect.',
                      ),
                    ),
                  ),
                if (_skipIfActiveEnabled &&
                    _healthConnectAvailable &&
                    !_stepsPermissionGranted)
                  _permissionBanner(
                    'Grant step-count access so reminders can be skipped '
                    "when you're already active.",
                    'Grant',
                    () => _channel.invokeMethod('requestStepsPermission'),
                  ),
                _statusCard(),
                if (_lastSkippedForActivity)
                  Card(
                    margin: const EdgeInsets.fromLTRB(12, 12, 12, 0),
                    color: _tone(Colors.green),
                    child: ListTile(
                      leading: const Icon(Icons.directions_walk),
                      title: const Text('Last reminder skipped'),
                      subtitle: Text(
                        'You took $_lastSkippedStepCount steps '
                        '(threshold: $_activityStepThreshold).',
                      ),
                    ),
                  ),
                SwitchListTile(
                  title: const Text('Reminders active'),
                  subtitle: Text(_active ? 'Reminders are on' : 'Paused'),
                  value: _active,
                  onChanged: _setActive,
                ),
                SwitchListTile(
                  title: const Text('Sound'),
                  subtitle: Text(
                    _soundEnabled ? 'Alarm sound + vibration' : 'Vibration only',
                  ),
                  value: _soundEnabled,
                  onChanged: _setSoundEnabled,
                ),
                SwitchListTile(
                  title: const Text('Skip if already active'),
                  subtitle: Text(
                    _canUseSkipIfActive
                        ? "Don't remind me if I've already taken 200+ steps "
                            'since the last reminder'
                        : 'Grant step access above to use this',
                  ),
                  value: _skipIfActiveEnabled,
                  onChanged: _canUseSkipIfActive ? _setSkipIfActiveEnabled : null,
                ),
              ],
            ),
    );
  }

  Widget _statusCard() {
    final now = DateTime.now();
    String title;
    String subtitle;
    IconData icon;
    Color color;

    if (_snoozedUntil != null && _snoozedUntil!.isAfter(now)) {
      title = 'Snoozed';
      subtitle =
          'Going off again at ${_formatClock(_snoozedUntil!)} '
          '(in ${_formatCountdown(_snoozedUntil!.difference(now))})';
      icon = Icons.snooze;
      color = _tone(Colors.blue);
    } else if (!_active) {
      title = 'Reminders paused';
      subtitle = "You won't get any reminders until you turn this back on.";
      icon = Icons.pause_circle_outline;
      color = _tone(Colors.grey, light: 200, dark: 800);
    } else {
      final next = _nextReminderTime();
      title = 'Next reminder';
      subtitle = next == null
          ? 'Unknown'
          : '${_formatClock(next)} (in ${_formatCountdown(next.difference(now))})';
      icon = Icons.timer_outlined;
      color = _tone(Colors.deepPurple);
    }

    final steps = _currentIntervalSteps;
    final showSteps =
        steps != null && _skipIfActiveEnabled && _snoozedUntil == null && _active;

    return Card(
      margin: const EdgeInsets.fromLTRB(12, 12, 12, 0),
      color: color,
      child: ListTile(
        leading: Icon(icon),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(subtitle),
            if (showSteps)
              Text('$steps/$_activityStepThreshold steps in this interval'),
          ],
        ),
      ),
    );
  }

  Widget _permissionBanner(String message, String actionLabel, VoidCallback onPressed) {
    return Card(
      margin: const EdgeInsets.all(12),
      color: _tone(Colors.amber, light: 100),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Expanded(child: Text(message)),
            TextButton(
              onPressed: () async {
                onPressed();
                await Future.delayed(const Duration(milliseconds: 500));
                await _refreshPermissionStatus();
              },
              child: Text(actionLabel),
            ),
          ],
        ),
      ),
    );
  }
}
