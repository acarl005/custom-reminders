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
      title: 'Reminders',
      theme: ThemeData(colorSchemeSeed: Colors.deepPurple, useMaterial3: true),
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
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
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
    setState(() {
      _active = !paused;
      _soundEnabled = soundEnabled;
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
    if (!mounted) return;
    setState(() {
      _canScheduleExactAlarms = canSchedule;
      _notificationsEnabled = notificationsEnabled;
      _ignoringBatteryOptimizations = ignoringBatteryOptimizations;
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Hourly Reminders')),
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
              ],
            ),
    );
  }

  Widget _permissionBanner(String message, String actionLabel, VoidCallback onPressed) {
    return Card(
      margin: const EdgeInsets.all(12),
      color: Colors.amber.shade100,
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
