import 'dart:async';

import 'package:flutter/material.dart';

import '../models/sensorhub_models.dart';
import '../state/sensorhub_controller.dart';
import '../theme/app_theme.dart';
import '../widgets/measurement_chart.dart';
import '../widgets/sensor_card.dart';
import '../widgets/status_widgets.dart';

class SensorHubShell extends StatefulWidget {
  const SensorHubShell({
    required this.controller,
    required this.enablePolling,
    super.key,
  });

  final SensorHubController controller;
  final bool enablePolling;

  @override
  State<SensorHubShell> createState() => _SensorHubShellState();
}

class _SensorHubShellState extends State<SensorHubShell> {
  int _index = 0;

  @override
  void initState() {
    super.initState();
    widget.controller.loadInitial(enablePolling: widget.enablePolling);
  }

  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final controller = widget.controller;
        final pages = [
          HomeScreen(
            controller: controller,
            enablePolling: widget.enablePolling,
            onOpenDevices: () => setState(() => _index = 1),
          ),
          DevicesScreen(controller: controller),
          EnvironmentsScreen(controller: controller),
          ProfileScreen(controller: controller),
        ];

        return Scaffold(
          appBar: AppBar(title: const Text('SensorHub')),
          body: switch (controller.state) {
            LoadState.loading ||
            LoadState.idle => const Center(child: CircularProgressIndicator()),
            LoadState.error => LoadStateView(
              icon: Icons.cloud_off,
              title: 'API indisponível',
              message:
                  controller.errorMessage ??
                  'Não foi possível carregar os dados.',
              action: FilledButton.icon(
                onPressed: () =>
                    controller.loadInitial(enablePolling: widget.enablePolling),
                icon: const Icon(Icons.refresh),
                label: const Text('Tentar novamente'),
              ),
            ),
            LoadState.ready => pages[_index],
          },
          bottomNavigationBar: BottomNavigationBar(
            currentIndex: _index,
            onTap: (index) => setState(() => _index = index),
            items: const [
              BottomNavigationBarItem(
                icon: Icon(Icons.dashboard),
                label: 'Início',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.sensors),
                label: 'Dispositivos',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.meeting_room),
                label: 'Ambientes',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.person),
                label: 'Perfil',
              ),
            ],
          ),
        );
      },
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    required this.controller,
    required this.enablePolling,
    required this.onOpenDevices,
    super.key,
  });

  final SensorHubController controller;
  final bool enablePolling;
  final VoidCallback onOpenDevices;

  @override
  Widget build(BuildContext context) {
    final cards = controller.sensorCards();
    final greeting = _HomeGreeting(user: controller.currentUser);
    if (cards.isEmpty) {
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          greeting,
          SizedBox(
            height: MediaQuery.sizeOf(context).height * 0.55,
            child: LoadStateView(
              icon: Icons.sensors_off,
              title: 'Nenhum sensor cadastrado',
              message: 'Cadastre um dispositivo para acompanhar as leituras.',
              action: OutlinedButton.icon(
                onPressed: onOpenDevices,
                icon: const Icon(Icons.add),
                label: const Text('Cadastrar dispositivo'),
              ),
            ),
          ),
        ],
      );
    }

    return RefreshIndicator(
      onRefresh: () async {
        await controller.loadDashboardDevices(notify: false);
        await controller.loadDashboardLatest();
      },
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        itemBuilder: (context, index) {
          if (index == 0) {
            return greeting;
          }

          final card = cards[index - 1];
          return SensorCard(
            card: card,
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => SensorDetailScreen(
                    controller: controller,
                    card: card,
                    enablePolling: enablePolling,
                  ),
                ),
              );
            },
          );
        },
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemCount: cards.length + 1,
      ),
    );
  }
}

class _HomeGreeting extends StatelessWidget {
  const _HomeGreeting({required this.user});

  final AppUser? user;

  @override
  Widget build(BuildContext context) {
    final name = user?.name.trim();
    final displayName = name == null || name.isEmpty ? 'usuário' : name;

    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${_greetingFor(DateTime.now())}, $displayName!',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 4),
          Text(
            'Acompanhe seus sensores em tempo real:',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }

  String _greetingFor(DateTime now) {
    if (now.hour < 12) {
      return 'Bom dia';
    }
    if (now.hour < 18) {
      return 'Boa tarde';
    }
    return 'Boa noite';
  }
}

class SensorDetailScreen extends StatefulWidget {
  const SensorDetailScreen({
    required this.controller,
    required this.card,
    required this.enablePolling,
    super.key,
  });

  final SensorHubController controller;
  final SensorCardData card;
  final bool enablePolling;

  @override
  State<SensorDetailScreen> createState() => _SensorDetailScreenState();
}

class _SensorDetailScreenState extends State<SensorDetailScreen> {
  DetailPeriod _period = DetailPeriod.sixHours;
  MeasurementOverview? _overview;
  Object? _loadError;
  bool _loading = true;
  Timer? _refreshTimer;
  bool _refreshing = false;
  int _requestGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
    if (widget.enablePolling) {
      _refreshTimer = Timer.periodic(
        SensorHubController.pollInterval,
        (_) => _refresh(),
      );
    }
  }

  Future<void> _load({bool keepCurrent = false}) async {
    final generation = ++_requestGeneration;
    final period = _period;
    if (!keepCurrent && mounted) {
      setState(() {
        _loading = true;
        _loadError = null;
      });
    }
    try {
      final overview = await widget.controller.loadOverview(
        deviceUuid: widget.card.device.deviceUuid,
        period: period,
      );
      if (!mounted || generation != _requestGeneration) {
        return;
      }
      setState(() {
        _overview = overview;
        _loadError = null;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || generation != _requestGeneration) {
        return;
      }
      setState(() {
        _loadError = error;
        _loading = false;
      });
    }
  }

  Future<void> _refresh() async {
    if (_refreshing || !mounted) {
      return;
    }
    _refreshing = true;
    try {
      await _load(keepCurrent: true);
    } finally {
      _refreshing = false;
    }
  }

  void _changePeriod(DetailPeriod period) {
    if (_period == period) {
      return;
    }
    setState(() => _period = period);
    _load();
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final overview = _overview;
    return Scaffold(
      appBar: AppBar(title: Text(widget.card.title)),
      body: switch ((_loading, _loadError, overview)) {
        (true, _, null) => const Center(child: CircularProgressIndicator()),
        (false, final error?, null) => LoadStateView(
          icon: Icons.error_outline,
          title: 'Não foi possível carregar o sensor',
          message: error.toString(),
          action: FilledButton.icon(
            onPressed: _load,
            icon: const Icon(Icons.refresh),
            label: const Text('Tentar novamente'),
          ),
        ),
        (_, _, final overview?) => RefreshIndicator(
          onRefresh: () => _load(keepCurrent: true),
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              _DetailHeader(card: widget.card, overview: overview),
              const SizedBox(height: 12),
              _PeriodSelector(value: _period, onChanged: _changePeriod),
              const SizedBox(height: 12),
              MeasurementChart(
                points: overview.series,
                metric: ChartMetric.temperature,
              ),
              const SizedBox(height: 12),
              MeasurementChart(
                points: overview.series,
                metric: ChartMetric.humidity,
              ),
              const SizedBox(height: 12),
              _OverviewPanel(stats: overview.overview),
            ],
          ),
        ),
        _ => const Center(child: CircularProgressIndicator()),
      },
    );
  }
}

class _DetailHeader extends StatelessWidget {
  const _DetailHeader({required this.card, required this.overview});

  final SensorCardData card;
  final MeasurementOverview overview;

  @override
  Widget build(BuildContext context) {
    final measurement = overview.latestMeasurement;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    card.title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                StatusPill(status: overview.freshnessStatus),
              ],
            ),
            const SizedBox(height: 8),
            Text(card.location),
            const SizedBox(height: 8),
            SelectableText(
              card.device.hardwareUuid,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: _CompactMetric(
                    label: 'Temperatura',
                    value: measurement == null
                        ? '--'
                        : '${measurement.temperature.toStringAsFixed(1)} °C',
                    color: AppColors.temperature,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _CompactMetric(
                    label: 'Umidade',
                    value: measurement == null
                        ? '--'
                        : '${measurement.humidity.toStringAsFixed(1)} %',
                    color: AppColors.humidity,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text('Última comunicação: ${formatDateTime(overview.lastSeenAt)}'),
          ],
        ),
      ),
    );
  }
}

class _CompactMetric extends StatelessWidget {
  const _CompactMetric({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.surfaceAlt,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 24,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _PeriodSelector extends StatelessWidget {
  const _PeriodSelector({required this.value, required this.onChanged});

  final DetailPeriod value;
  final ValueChanged<DetailPeriod> onChanged;

  @override
  Widget build(BuildContext context) {
    return SegmentedButton<DetailPeriod>(
      segments: DetailPeriod.values
          .map(
            (period) => ButtonSegment<DetailPeriod>(
              value: period,
              label: Text(period.label),
            ),
          )
          .toList(),
      selected: {value},
      onSelectionChanged: (selection) => onChanged(selection.first),
      showSelectedIcon: false,
    );
  }
}

class _OverviewPanel extends StatelessWidget {
  const _OverviewPanel({required this.stats});

  final OverviewStats? stats;

  @override
  Widget build(BuildContext context) {
    if (stats == null) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: Text('Overview indisponível para o período selecionado.'),
        ),
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Overview do período',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            _OverviewRow(
              label: 'Temperatura máxima',
              value: '${stats!.temperatureMax.toStringAsFixed(1)} °C',
              when: stats!.temperatureMaxAt,
            ),
            _OverviewRow(
              label: 'Temperatura mínima',
              value: '${stats!.temperatureMin.toStringAsFixed(1)} °C',
              when: stats!.temperatureMinAt,
            ),
            _OverviewRow(
              label: 'Temperatura média',
              value: '${stats!.temperatureAverage.toStringAsFixed(1)} °C',
            ),
            _OverviewRow(
              label: 'Umidade máxima',
              value: '${stats!.humidityMax.toStringAsFixed(1)} %',
              when: stats!.humidityMaxAt,
            ),
            _OverviewRow(
              label: 'Umidade mínima',
              value: '${stats!.humidityMin.toStringAsFixed(1)} %',
              when: stats!.humidityMinAt,
            ),
            _OverviewRow(
              label: 'Umidade média',
              value: '${stats!.humidityAverage.toStringAsFixed(1)} %',
            ),
            _OverviewRow(
              label: 'Medições consideradas',
              value: '${stats!.measurementCount}',
            ),
          ],
        ),
      ),
    );
  }
}

class _OverviewRow extends StatelessWidget {
  const _OverviewRow({required this.label, required this.value, this.when});

  final String label;
  final String value;
  final DateTime? when;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(value, style: const TextStyle(fontWeight: FontWeight.w700)),
              if (when != null)
                Text(
                  formatDateTime(when),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class DevicesScreen extends StatelessWidget {
  const DevicesScreen({required this.controller, super.key});

  final SensorHubController controller;

  @override
  Widget build(BuildContext context) {
    return _ListPage(
      title: 'Dispositivos',
      icon: Icons.add,
      actionLabel: 'Novo dispositivo',
      onAction: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => DeviceFormScreen(controller: controller),
        ),
      ),
      emptyTitle: 'Nenhum dispositivo',
      emptyMessage: 'Cadastre o UUID físico de um sensor.',
      children: controller.devices
          .map(
            (device) => Card(
              child: ListTile(
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => DeviceFormScreen(
                      controller: controller,
                      device: device,
                    ),
                  ),
                ),
                leading: const Icon(Icons.sensors, color: AppColors.primary),
                title: Text(
                  isBlank(device.name) ? 'Sensor sem nome' : device.name!,
                ),
                subtitle: Text(
                  '${controller.environmentName(device.environmentUuid)}\n${device.hardwareUuid}',
                ),
                isThreeLine: true,
                trailing: StatusPill(
                  status: device.status == 'INACTIVATED'
                      ? 'INACTIVATED'
                      : 'ACTIVATED',
                ),
              ),
            ),
          )
          .toList(),
    );
  }
}

class EnvironmentsScreen extends StatelessWidget {
  const EnvironmentsScreen({required this.controller, super.key});

  final SensorHubController controller;

  @override
  Widget build(BuildContext context) {
    return _ListPage(
      title: 'Ambientes',
      icon: Icons.add,
      actionLabel: 'Novo ambiente',
      onAction: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => EnvironmentFormScreen(controller: controller),
        ),
      ),
      emptyTitle: 'Nenhum ambiente',
      emptyMessage: 'Crie ambientes para organizar seus sensores.',
      children: controller.environments
          .map(
            (environment) => Card(
              child: ListTile(
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => EnvironmentFormScreen(
                      controller: controller,
                      environment: environment,
                    ),
                  ),
                ),
                leading: const Icon(
                  Icons.meeting_room,
                  color: AppColors.accent,
                ),
                title: Text(environment.name),
                subtitle: Text(environment.uuid),
              ),
            ),
          )
          .toList(),
    );
  }
}

class _ListPage extends StatelessWidget {
  const _ListPage({
    required this.title,
    required this.icon,
    required this.actionLabel,
    required this.onAction,
    required this.emptyTitle,
    required this.emptyMessage,
    required this.children,
  });

  final String title;
  final IconData icon;
  final String actionLabel;
  final VoidCallback onAction;
  final String emptyTitle;
  final String emptyMessage;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Row(
          children: [
            Expanded(
              child: Text(title, style: Theme.of(context).textTheme.titleLarge),
            ),
            FilledButton.icon(
              onPressed: onAction,
              icon: Icon(icon),
              label: Text(actionLabel),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (children.isEmpty)
          LoadStateView(
            icon: Icons.inbox,
            title: emptyTitle,
            message: emptyMessage,
          )
        else
          ...children,
      ],
    );
  }
}

class DeviceFormScreen extends StatefulWidget {
  const DeviceFormScreen({required this.controller, this.device, super.key});

  final SensorHubController controller;
  final Device? device;

  @override
  State<DeviceFormScreen> createState() => _DeviceFormScreenState();
}

class _DeviceFormScreenState extends State<DeviceFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _hardwareController = TextEditingController();
  final _nameController = TextEditingController();
  String? _environmentUuid;
  bool _saving = false;
  String? _error;
  bool get _editing => widget.device != null;

  @override
  void initState() {
    super.initState();
    final device = widget.device;
    if (device != null) {
      _hardwareController.text = device.hardwareUuid;
      _nameController.text = device.name ?? '';
      _environmentUuid = device.environmentUuid;
    }
  }

  @override
  void dispose() {
    _hardwareController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_editing ? 'Editar dispositivo' : 'Novo dispositivo'),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _hardwareController,
              readOnly: _editing,
              decoration: const InputDecoration(labelText: 'UUID do hardware'),
              validator: _requiredUuid,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(labelText: 'Nome do sensor'),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String?>(
              initialValue: _environmentUuid,
              decoration: const InputDecoration(labelText: 'Ambiente'),
              items: [
                const DropdownMenuItem<String?>(
                  value: null,
                  child: Text('Sem ambiente'),
                ),
                ...widget.controller.environments.map(
                  (environment) => DropdownMenuItem<String?>(
                    value: environment.uuid,
                    child: Text(environment.name),
                  ),
                ),
              ],
              onChanged: (value) => setState(() => _environmentUuid = value),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: AppColors.danger)),
            ],
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save),
              label: Text(
                _editing ? 'Salvar alterações' : 'Salvar dispositivo',
              ),
            ),
            if (_editing) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _saving ? null : _toggleStatus,
                icon: Icon(
                  widget.device?.status == 'INACTIVATED'
                      ? Icons.play_circle
                      : Icons.pause_circle,
                ),
                label: Text(
                  widget.device?.status == 'INACTIVATED'
                      ? 'Reativar dispositivo'
                      : 'Inativar dispositivo',
                ),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _saving ? null : _confirmDelete,
                icon: const Icon(Icons.delete),
                label: const Text('Excluir dispositivo'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.danger,
                  side: const BorderSide(color: AppColors.danger),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final name = _nameController.text.trim().isEmpty
          ? null
          : _nameController.text.trim();
      final device = widget.device;
      if (device == null) {
        await widget.controller.createDevice(
          hardwareUuid: _hardwareController.text.trim(),
          environmentUuid: _environmentUuid,
          name: name,
        );
      } else {
        await widget.controller.updateDevice(
          deviceUuid: device.uuid,
          environmentUuid: _environmentUuid,
          name: name,
          status: device.status,
        );
      }
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _toggleStatus() async {
    final device = widget.device;
    if (device == null) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      if (device.status == 'INACTIVATED') {
        await widget.controller.reactivateDevice(device);
      } else {
        await widget.controller.inactivateDevice(device);
      }
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _confirmDelete() async {
    final device = widget.device;
    if (device == null) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Excluir dispositivo?'),
        content: const Text(
          'Todas as medições relacionadas a este sensor serão perdidas. '
          'Esta ação não pode ser revertida.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(true),
            icon: const Icon(Icons.delete),
            label: const Text('Excluir'),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.danger,
              foregroundColor: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.controller.deleteDevice(device.uuid);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }
}

class EnvironmentFormScreen extends StatefulWidget {
  const EnvironmentFormScreen({
    required this.controller,
    this.environment,
    super.key,
  });

  final SensorHubController controller;
  final SensorEnvironment? environment;

  @override
  State<EnvironmentFormScreen> createState() => _EnvironmentFormScreenState();
}

class _EnvironmentFormScreenState extends State<EnvironmentFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  bool _saving = false;
  String? _error;
  bool get _editing => widget.environment != null;

  @override
  void initState() {
    super.initState();
    final environment = widget.environment;
    if (environment != null) {
      _nameController.text = environment.name;
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_editing ? 'Editar ambiente' : 'Novo ambiente'),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(labelText: 'Nome do ambiente'),
              validator: (value) => value == null || value.trim().isEmpty
                  ? 'Informe o nome.'
                  : null,
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: const TextStyle(color: AppColors.danger)),
            ],
            const SizedBox(height: 20),
            FilledButton.icon(
              onPressed: _saving ? null : _save,
              icon: _saving
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.save),
              label: Text(_editing ? 'Salvar alterações' : 'Salvar ambiente'),
            ),
            if (_editing) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _saving ? null : _confirmDelete,
                icon: const Icon(Icons.delete),
                label: const Text('Excluir ambiente'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.danger,
                  side: const BorderSide(color: AppColors.danger),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final environment = widget.environment;
      if (environment == null) {
        await widget.controller.createEnvironment(_nameController.text.trim());
      } else {
        await widget.controller.updateEnvironment(
          environmentUuid: environment.uuid,
          name: _nameController.text.trim(),
        );
      }
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (error) {
      setState(() => _error = error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  Future<void> _confirmDelete() async {
    final environment = widget.environment;
    if (environment == null) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Excluir ambiente?'),
        content: const Text(
          'O ambiente só pode ser excluído se não houver dispositivos vinculados.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton.icon(
            onPressed: () => Navigator.of(context).pop(true),
            icon: const Icon(Icons.delete),
            label: const Text('Excluir'),
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.danger,
              foregroundColor: AppColors.textPrimary,
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.controller.deleteEnvironment(environment.uuid);
      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (_) {
      setState(
        () => _error =
            'Não foi possível excluir. Remova, inative ou reassocie os dispositivos vinculados antes de excluir este ambiente.',
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }
}

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({required this.controller, super.key});

  final SensorHubController controller;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _emailController;
  bool _saving = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    final user = widget.controller.currentUser;
    _nameController = TextEditingController(text: user?.name ?? '');
    _emailController = TextEditingController(text: user?.email ?? '');
  }

  @override
  void dispose() {
    _nameController.dispose();
    _emailController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Perfil', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextFormField(
                    controller: _nameController,
                    decoration: const InputDecoration(labelText: 'Nome'),
                    validator: (value) => value == null || value.trim().isEmpty
                        ? 'Informe o nome.'
                        : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _emailController,
                    readOnly: true,
                    decoration: const InputDecoration(labelText: 'Email'),
                  ),
                  if (_message != null) ...[
                    const SizedBox(height: 12),
                    Text(
                      _message!,
                      style: const TextStyle(color: AppColors.primary),
                    ),
                  ],
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: _saving ? null : _save,
                    icon: _saving
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.save),
                    label: const Text('Salvar perfil'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() {
      _saving = true;
      _message = null;
    });
    try {
      await widget.controller.updateUser(name: _nameController.text.trim());
      setState(() => _message = 'Perfil atualizado.');
    } catch (error) {
      setState(() => _message = error.toString());
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }
}

String? _requiredUuid(String? value) {
  final text = value?.trim() ?? '';
  final uuid = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  if (!uuid.hasMatch(text)) {
    return 'Informe um UUID válido.';
  }
  return null;
}
