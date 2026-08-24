import 'dart:convert';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:url_launcher/url_launcher.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('pt_BR');
  runApp(const MeetingAssistantApp());
}

class MeetingAssistantApp extends StatelessWidget {
  const MeetingAssistantApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xff4a6da7);
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Minha Semana',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: seed),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xffdfe9f5),
        cardTheme: const CardThemeData(elevation: 0, margin: EdgeInsets.zero),
        inputDecorationTheme: const InputDecorationTheme(
          border: OutlineInputBorder(),
          filled: true,
          fillColor: Colors.white,
        ),
      ),
      home: const HomePage(),
    );
  }
}

class Assignment {
  Assignment({
    required this.id,
    required this.title,
    required this.type,
    required this.date,
    this.details = '',
    this.notes = '',
    this.durationMinutes = 5,
    this.steps = const [false, false, false, false],
  });

  final String id;
  final String title;
  final String type;
  final DateTime date;
  String details;
  String notes;
  int durationMinutes;
  List<bool> steps;

  double get progress => steps.where((e) => e).length / steps.length;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'type': type,
        'date': date.toIso8601String(),
        'details': details,
        'notes': notes,
        'durationMinutes': durationMinutes,
        'steps': steps,
      };

  factory Assignment.fromJson(Map<String, dynamic> json) => Assignment(
        id: json['id'] as String,
        title: json['title'] as String,
        type: json['type'] as String,
        date: DateTime.parse(json['date'] as String),
        details: json['details'] as String? ?? '',
        notes: json['notes'] as String? ?? '',
        durationMinutes: json['durationMinutes'] as int? ?? 5,
        steps: (json['steps'] as List? ?? [false, false, false, false])
            .map((e) => e as bool)
            .toList(),
      );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _tab = 0;
  bool _loading = true;
  List<Assignment> _items = [];
  int _midweekDay = DateTime.thursday;
  int _weekendDay = DateTime.sunday;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('assignments');
    setState(() {
      if (raw != null) {
        _items = (jsonDecode(raw) as List)
            .map((e) => Assignment.fromJson(e as Map<String, dynamic>))
            .toList();
      }
      _midweekDay = prefs.getInt('midweekDay') ?? DateTime.thursday;
      _weekendDay = prefs.getInt('weekendDay') ?? DateTime.sunday;
      _loading = false;
    });
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'assignments',
      jsonEncode(_items.map((e) => e.toJson()).toList()),
    );
    await prefs.setInt('midweekDay', _midweekDay);
    await prefs.setInt('weekendDay', _weekendDay);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    final pages = [
      _Dashboard(items: _items, onOpen: _openAssignment, onLink: _openUrl),
      _WeekPage(items: _items, onOpen: _openAssignment),
      _LinksPage(onOpen: _openUrl),
      _SettingsPage(
        midweekDay: _midweekDay,
        weekendDay: _weekendDay,
        onChanged: (m, w) { setState(() { _midweekDay = m; _weekendDay = w; }); _save(); },
      ),
    ];
    return Scaffold(
      appBar: AppBar(
        title: const Text('Minha Semana'),
        actions: [IconButton(onPressed: _showInfo, icon: const Icon(Icons.info_outline))],
      ),
      body: SafeArea(child: pages[_tab]),
      floatingActionButton: _tab == 0
          ? FloatingActionButton.extended(onPressed: () => showModalBottomSheet(context: context, builder: (_) => const _AssistantPreview()), icon: const Icon(Icons.mic), label: const Text('Perguntar'))
          : _tab == 1 ? FloatingActionButton.extended(onPressed: _newAssignment, icon: const Icon(Icons.add), label: const Text('Designação')) : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (value) => setState(() => _tab = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Hoje'),
          NavigationDestination(icon: Icon(Icons.calendar_month_outlined), selectedIcon: Icon(Icons.calendar_month), label: 'Semana'),
          NavigationDestination(icon: Icon(Icons.menu_book_outlined), selectedIcon: Icon(Icons.menu_book), label: 'Publicações'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'Ajustes'),
        ],
      ),
    );
  }

  Future<void> _newAssignment() async {
    final item = await Navigator.push<Assignment>(
      context,
      MaterialPageRoute(builder: (_) => const EditAssignmentPage()),
    );
    if (item != null) { setState(() => _items.add(item)); await _save(); }
  }

  Future<void> _openAssignment(Assignment item) async {
    final result = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => AssignmentPage(item: item, onSave: _save)),
    );
    if (result == 'delete') { setState(() => _items.removeWhere((e) => e.id == item.id)); await _save(); }
    setState(() {});
  }

  Future<void> _openUrl(String value) async {
    final uri = Uri.parse(value);
    if (!await launchUrl(uri, mode: LaunchMode.externalApplication) && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Não foi possível abrir o link.')));
    }
  }

  void _showInfo() => showAboutDialog(
        context: context,
        applicationName: 'Minha Semana',
        applicationVersion: '1.0.0',
        children: const [Text('Assistente pessoal não oficial. O conteúdo das publicações é aberto somente nos canais oficiais.')],
      );
}

class _Dashboard extends StatelessWidget {
  const _Dashboard({required this.items, required this.onOpen, required this.onLink});
  final List<Assignment> items;
  final ValueChanged<Assignment> onOpen;
  final ValueChanged<String> onLink;

  @override
  Widget build(BuildContext context) {
    final sorted = [...items]..sort((a, b) => a.date.compareTo(b.date));
    final upcoming = sorted.where((e) => !e.date.isBefore(DateTime.now().subtract(const Duration(days: 1)))).toList();
    final average = items.isEmpty ? 0.0 : items.fold<double>(0, (p, e) => p + e.progress) / items.length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 96),
      children: [
        Text('Olá, Rafael', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text(DateFormat("EEEE, d 'de' MMMM", 'pt_BR').format(DateTime.now())),
        const SizedBox(height: 18),
        _DailyStudyCard(onOpen: () => onLink('https://wol.jw.org/pt/wol/h/r5/lp-t')),
        const SizedBox(height: 22),
        const _SectionTitle('Reunião desta semana'),
        const SizedBox(height: 10),
        _MeetingOverview(onOpen: () => onLink('https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/')),
        const SizedBox(height: 22),
        Card(
          color: Theme.of(context).colorScheme.primaryContainer,
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Preparação da semana', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17)),
              const SizedBox(height: 12),
              LinearProgressIndicator(value: average, minHeight: 10, borderRadius: BorderRadius.circular(8)),
              const SizedBox(height: 8),
              Text('${(average * 100).round()}% concluído'),
            ]),
          ),
        ),
        const SizedBox(height: 22),
        const _SectionTitle('Próximas designações'),
        const SizedBox(height: 10),
        if (upcoming.isEmpty)
          const _EmptyCard()
        else
          ...upcoming.take(4).map((e) => Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: _AssignmentCard(item: e, onTap: () => onOpen(e)),
              )),
        const SizedBox(height: 14),
        const _SectionTitle('Plano simples para hoje'),
        const SizedBox(height: 10),
        const Card(child: ListTile(
          leading: CircleAvatar(child: Icon(Icons.timer_outlined)),
          title: Text('Separe de 10 a 15 minutos'),
          subtitle: Text('Leia o ponto principal, explique com suas palavras e faça um ensaio curto.'),
        )),
      ],
    );
  }
}

class _DailyStudyCard extends StatelessWidget {
  const _DailyStudyCard({required this.onOpen});
  final VoidCallback onOpen;
  @override
  Widget build(BuildContext context) => Card(
    color: Colors.white,
    child: Padding(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Container(padding: const EdgeInsets.all(9), decoration: BoxDecoration(color: const Color(0xffdce8f8), borderRadius: BorderRadius.circular(10)), child: const Icon(Icons.wb_sunny_outlined, color: Color(0xff315f9b))),
        const SizedBox(width: 12),
        const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('TEXTO DIÁRIO', style: TextStyle(color: Color(0xff315f9b), fontWeight: FontWeight.bold)), Text('Um pensamento para começar o dia')]))
      ]),
      const Divider(height: 28),
      const Text('Em poucas palavras', style: TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 5),
      const Text('Leia o texto oficial e identifique o que ele revela sobre Jeová, sobre nossas escolhas e sobre como tratar outras pessoas.'),
      const SizedBox(height: 14),
      const Text('Aplicação para hoje', style: TextStyle(fontWeight: FontWeight.bold)),
      const SizedBox(height: 5),
      const Text('Escolha uma atitude prática para colocar em ação ainda hoje. Uma aplicação pequena e específica é mais fácil de lembrar.'),
      const SizedBox(height: 14),
      Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: const Color(0xfffff5dc), borderRadius: BorderRadius.circular(10)), child: const Row(crossAxisAlignment: CrossAxisAlignment.start, children: [Icon(Icons.psychology_outlined), SizedBox(width: 10), Expanded(child: Text('Para meditar: Como esse conselho pode influenciar uma decisão minha hoje?'))])),
      const SizedBox(height: 12),
      OutlinedButton.icon(onPressed: onOpen, icon: const Icon(Icons.open_in_new), label: const Text('Ler o texto oficial de hoje')),
    ])),
  );
}

class _MeetingOverview extends StatelessWidget {
  const _MeetingOverview({required this.onOpen});
  final VoidCallback onOpen;
  @override
  Widget build(BuildContext context) => Card(child: Padding(padding: const EdgeInsets.all(14), child: Column(children: [
    const _MeetingPart(icon: Icons.diamond_outlined, color: Color(0xff4f5b66), title: 'Tesouros da Palavra de Deus', subtitle: 'Ideia principal • textos • aplicação'),
    const Divider(),
    const _MeetingPart(icon: Icons.search, color: Color(0xff65727d), title: 'Joias Espirituais', subtitle: 'Descobertas • contexto • como aplicar'),
    const Divider(),
    const _MeetingPart(icon: Icons.forum_outlined, color: Color(0xffb08332), title: 'Faça Seu Melhor no Ministério', subtitle: 'Objetivo da lição • sugestão de conversa'),
    const Divider(),
    const _MeetingPart(icon: Icons.favorite_outline, color: Color(0xff8b5261), title: 'Nossa Vida Cristã', subtitle: 'Resumo • como usar na família e congregação'),
    const Divider(),
    const _MeetingPart(icon: Icons.menu_book_outlined, color: Color(0xff315f9b), title: 'Estudo Bíblico de Congregação', subtitle: 'Pontos principais • perguntas para recordar'),
    const SizedBox(height: 8),
    FilledButton.icon(onPressed: onOpen, icon: const Icon(Icons.calendar_month), label: const Text('Abrir programação oficial')),
  ])));
}

class _MeetingPart extends StatelessWidget {
  const _MeetingPart({required this.icon, required this.color, required this.title, required this.subtitle});
  final IconData icon; final Color color; final String title, subtitle;
  @override Widget build(BuildContext context) => ListTile(contentPadding: EdgeInsets.zero, leading: CircleAvatar(backgroundColor: color.withValues(alpha: .14), child: Icon(icon, color: color)), title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)), subtitle: Text(subtitle), trailing: const Icon(Icons.chevron_right), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => StudyDetailPage(title: title, color: color))));
}

class StudyDetailPage extends StatelessWidget {
  const StudyDetailPage({super.key, required this.title, required this.color});
  final String title; final Color color;
  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(title), backgroundColor: color, foregroundColor: Colors.white),
    body: ListView(padding: const EdgeInsets.all(16), children: [
      _StudyBlock(color: color, icon: Icons.lightbulb_outline, title: 'Resumo e explicação', text: 'Identifique a ideia central da matéria e explique com palavras simples. Observe o contexto dos textos e como cada ponto fortalece a ideia principal.'),
      _StudyBlock(color: color, icon: Icons.compare_arrows, title: 'Uma comparação', text: 'Pense numa situação comum que tenha o mesmo princípio. Comparações curtas ajudam a visualizar o ensino e a lembrá-lo durante a semana.'),
      _StudyBlock(color: color, icon: Icons.person_outline, title: 'Como aplicar no dia a dia', text: 'Escolha uma atitude específica que você pode praticar hoje. Pergunte-se: “O que devo começar, continuar ou evitar?”'),
      _StudyBlock(color: color, icon: Icons.family_restroom, title: 'Como aplicar na família', text: 'Procure uma maneira bondosa de usar o princípio para ouvir melhor, demonstrar respeito, perdoar ou fortalecer a espiritualidade da família.'),
      _StudyBlock(color: color, icon: Icons.groups_outlined, title: 'Como aplicar no campo', text: 'Transforme a ideia numa pergunta simples, escolha um texto principal e pense em como explicá-lo sem palavras difíceis.'),
      _StudyBlock(color: color, icon: Icons.psychology_outlined, title: 'Perguntas para recordar', text: 'O que isso me ensina sobre Jeová? Qual é o princípio? Em que situação vou precisar dele? Como explicaria este ponto em trinta segundos?'),
      const SizedBox(height: 8),
      const TextField(maxLines: 7, decoration: InputDecoration(labelText: 'Minhas joias e anotações', hintText: 'Escreva palavras-chave e aplicações pessoais...')),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: () {}, icon: const Icon(Icons.mic_none), label: const Text('Perguntar ao assistente — em breve')),
    ]));
}

class _StudyBlock extends StatelessWidget {
  const _StudyBlock({required this.color, required this.icon, required this.title, required this.text});
  final Color color; final IconData icon; final String title, text;
  @override Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(bottom: 12), child: Card(color: color.withValues(alpha: .10), child: Padding(padding: const EdgeInsets.all(15), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Row(children: [Icon(icon, color: color), const SizedBox(width: 9), Expanded(child: Text(title, style: TextStyle(fontWeight: FontWeight.bold, color: color, fontSize: 16)))]), const SizedBox(height: 8), Text(text)]))));
}

class _AssistantPreview extends StatelessWidget {
  const _AssistantPreview();
  @override Widget build(BuildContext context) => Padding(padding: const EdgeInsets.all(24), child: SafeArea(child: Column(mainAxisSize: MainAxisSize.min, children: [
    const CircleAvatar(radius: 30, backgroundColor: Color(0xff315f9b), child: Icon(Icons.auto_awesome, color: Colors.white, size: 30)),
    const SizedBox(height: 12),
    const Text('Assistente de estudo', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
    const SizedBox(height: 8),
    const Text('O microfone ficará aqui. Na próxima etapa, ele poderá responder usando o texto diário ou a parte da reunião que estiver aberta.', textAlign: TextAlign.center),
    const SizedBox(height: 16),
    FilledButton.icon(onPressed: () => Navigator.pop(context), icon: const Icon(Icons.mic_none), label: const Text('Ativação futura')),
  ])));
}

class _WeekPage extends StatelessWidget {
  const _WeekPage({required this.items, required this.onOpen});
  final List<Assignment> items;
  final ValueChanged<Assignment> onOpen;
  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const Center(child: Padding(padding: EdgeInsets.all(28), child: _EmptyCard()));
    final sorted = [...items]..sort((a, b) => a.date.compareTo(b.date));
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
      itemCount: sorted.length,
      separatorBuilder: (_, __) => const SizedBox(height: 10),
      itemBuilder: (_, i) => _AssignmentCard(item: sorted[i], onTap: () => onOpen(sorted[i])),
    );
  }
}

class _AssignmentCard extends StatelessWidget {
  const _AssignmentCard({required this.item, required this.onTap});
  final Assignment item;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(children: [
              CircleAvatar(child: Icon(item.type == 'Saída de campo' ? Icons.groups_outlined : Icons.record_voice_over_outlined)),
              const SizedBox(width: 12),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(item.title, style: const TextStyle(fontWeight: FontWeight.bold)),
                Text('${item.type} • ${DateFormat('dd/MM').format(item.date)}'),
                const SizedBox(height: 8),
                LinearProgressIndicator(value: item.progress, borderRadius: BorderRadius.circular(6)),
              ])),
              const Icon(Icons.chevron_right),
            ]),
          ),
        ),
      );
}

class EditAssignmentPage extends StatefulWidget {
  const EditAssignmentPage({super.key});
  @override
  State<EditAssignmentPage> createState() => _EditAssignmentPageState();
}

class _EditAssignmentPageState extends State<EditAssignmentPage> {
  final _form = GlobalKey<FormState>();
  final _title = TextEditingController();
  final _details = TextEditingController();
  String _type = 'Reunião do meio de semana';
  DateTime _date = DateTime.now().add(const Duration(days: 7));
  int _duration = 5;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Nova designação')),
        body: Form(
          key: _form,
          child: ListView(padding: const EdgeInsets.all(16), children: [
            TextFormField(controller: _title, decoration: const InputDecoration(labelText: 'Tema ou nome da parte'), validator: (v) => v == null || v.trim().isEmpty ? 'Informe o tema.' : null),
            const SizedBox(height: 14),
            DropdownButtonFormField<String>(value: _type, decoration: const InputDecoration(labelText: 'Atividade'), items: const [
              DropdownMenuItem(value: 'Reunião do meio de semana', child: Text('Reunião do meio de semana')),
              DropdownMenuItem(value: 'Estudo de A Sentinela', child: Text('Estudo de A Sentinela')),
              DropdownMenuItem(value: 'Saída de campo', child: Text('Saída de campo')),
            ], onChanged: (v) => setState(() => _type = v!)),
            const SizedBox(height: 14),
            Card(child: ListTile(leading: const Icon(Icons.calendar_today), title: const Text('Data'), subtitle: Text(DateFormat('dd/MM/yyyy').format(_date)), onTap: _pickDate)),
            const SizedBox(height: 14),
            DropdownButtonFormField<int>(value: _duration, decoration: const InputDecoration(labelText: 'Tempo da parte'), items: [1, 2, 3, 4, 5, 6, 8, 10, 15, 30, 60].map((e) => DropdownMenuItem(value: e, child: Text('$e minutos'))).toList(), onChanged: (v) => setState(() => _duration = v!)),
            const SizedBox(height: 14),
            TextFormField(controller: _details, maxLines: 6, decoration: const InputDecoration(labelText: 'Informações recebidas', hintText: 'Cole aqui o texto da designação, textos bíblicos, lição e observações.')),
            const SizedBox(height: 22),
            FilledButton.icon(onPressed: _submit, icon: const Icon(Icons.check), label: const Text('Criar plano da semana')),
          ]),
        ),
      );

  Future<void> _pickDate() async {
    final value = await showDatePicker(context: context, initialDate: _date, firstDate: DateTime.now().subtract(const Duration(days: 30)), lastDate: DateTime.now().add(const Duration(days: 730)));
    if (value != null) setState(() => _date = value);
  }

  void _submit() {
    if (!_form.currentState!.validate()) return;
    Navigator.pop(context, Assignment(id: DateTime.now().microsecondsSinceEpoch.toString(), title: _title.text.trim(), type: _type, date: _date, details: _details.text.trim(), durationMinutes: _duration));
  }
}

class AssignmentPage extends StatefulWidget {
  const AssignmentPage({super.key, required this.item, required this.onSave});
  final Assignment item;
  final Future<void> Function() onSave;
  @override
  State<AssignmentPage> createState() => _AssignmentPageState();
}

class _AssignmentPageState extends State<AssignmentPage> {
  late final TextEditingController _notes;
  final labels = const [
    ('Entender', 'Leia as informações e identifique uma ideia principal.'),
    ('Organizar', 'Monte introdução, pontos principais e conclusão com suas palavras.'),
    ('Memorizar', 'Explique cada ponto sem ler e anote somente palavras-chave.'),
    ('Ensaiar', 'Faça um ensaio cronometrado e ajuste o que ultrapassar o tempo.'),
  ];

  @override
  void initState() { super.initState(); _notes = TextEditingController(text: widget.item.notes); }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Preparação'), actions: [PopupMenuButton<String>(onSelected: (v) { if (v == 'delete') _delete(); }, itemBuilder: (_) => const [PopupMenuItem(value: 'delete', child: Text('Excluir designação'))])]),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          Text(widget.item.title, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text('${widget.item.type} • ${DateFormat('dd/MM/yyyy').format(widget.item.date)} • ${widget.item.durationMinutes} min'),
          const SizedBox(height: 18),
          if (widget.item.details.isNotEmpty) Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [const Text('Informações da designação', style: TextStyle(fontWeight: FontWeight.bold)), const SizedBox(height: 8), Text(widget.item.details)]))),
          const SizedBox(height: 18),
          const _SectionTitle('Plano de preparação'),
          const SizedBox(height: 8),
          ...List.generate(labels.length, (i) => Card(child: CheckboxListTile(value: widget.item.steps[i], title: Text(labels[i].$1), subtitle: Text(labels[i].$2), onChanged: (v) async { setState(() => widget.item.steps[i] = v!); await widget.onSave(); }))),
          const SizedBox(height: 18),
          TextField(controller: _notes, maxLines: 8, decoration: const InputDecoration(labelText: 'Minhas palavras-chave e anotações'), onChanged: (v) { widget.item.notes = v; widget.onSave(); }),
          const SizedBox(height: 18),
          FilledButton.icon(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => RehearsalPage(minutes: widget.item.durationMinutes))), icon: const Icon(Icons.timer_outlined), label: const Text('Iniciar ensaio')),
        ]),
      );

  Future<void> _delete() async {
    final yes = await showDialog<bool>(context: context, builder: (_) => AlertDialog(title: const Text('Excluir?'), content: const Text('A designação e as anotações serão removidas.'), actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')), FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Excluir'))]));
    if (yes == true && mounted) Navigator.pop(context, 'delete');
  }
}

class RehearsalPage extends StatefulWidget {
  const RehearsalPage({super.key, required this.minutes});
  final int minutes;
  @override
  State<RehearsalPage> createState() => _RehearsalPageState();
}

class _RehearsalPageState extends State<RehearsalPage> {
  Timer? timer;
  int elapsed = 0;
  bool running = false;
  void toggle() {
    if (running) { timer?.cancel(); setState(() => running = false); return; }
    setState(() => running = true);
    timer = Timer.periodic(const Duration(seconds: 1), (_) => setState(() => elapsed++));
  }
  @override
  void dispose() { timer?.cancel(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    final target = widget.minutes * 60;
    final over = elapsed > target;
    final text = '${(elapsed ~/ 60).toString().padLeft(2, '0')}:${(elapsed % 60).toString().padLeft(2, '0')}';
    return Scaffold(appBar: AppBar(title: const Text('Ensaio')), body: Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      Text('Tempo previsto: ${widget.minutes} min'),
      const SizedBox(height: 22),
      Text(text, style: TextStyle(fontSize: 64, fontWeight: FontWeight.bold, color: over ? Colors.red : null)),
      if (over) const Text('O tempo previsto terminou. Conclua a ideia com calma.'),
      const SizedBox(height: 30),
      FilledButton.icon(onPressed: toggle, icon: Icon(running ? Icons.pause : Icons.play_arrow), label: Text(running ? 'Pausar' : 'Começar')),
      TextButton(onPressed: () { timer?.cancel(); setState(() { elapsed = 0; running = false; }); }, child: const Text('Reiniciar')),
    ]))));
  }
}

class _LinksPage extends StatelessWidget {
  const _LinksPage({required this.onOpen});
  final ValueChanged<String> onOpen;
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [
    const Text('Conteúdo oficial', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
    const SizedBox(height: 6),
    const Text('As publicações são abertas diretamente nos canais oficiais.'),
    const SizedBox(height: 16),
    _LinkCard(title: 'Apostila da reunião', subtitle: 'Programação e matérias da reunião do meio de semana', icon: Icons.calendar_month, onTap: () => onOpen('https://www.jw.org/pt/biblioteca/jw-apostila-do-mes/')),
    _LinkCard(title: 'A Sentinela — Estudo', subtitle: 'Edições para o estudo congregacional', icon: Icons.menu_book, onTap: () => onOpen('https://www.jw.org/pt/biblioteca/revistas/')),
    _LinkCard(title: 'Bíblia on-line', subtitle: 'Tradução do Novo Mundo', icon: Icons.auto_stories, onTap: () => onOpen('https://www.jw.org/pt/biblioteca/biblia/biblia-de-estudo/livros/')),
    _LinkCard(title: 'Ensinos bíblicos', subtitle: 'Artigos e recursos para o ministério', icon: Icons.groups, onTap: () => onOpen('https://www.jw.org/pt/ensinos-biblicos/')),
  ]);
}

class _LinkCard extends StatelessWidget {
  const _LinkCard({required this.title, required this.subtitle, required this.icon, required this.onTap});
  final String title, subtitle; final IconData icon; final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(bottom: 10), child: Card(child: ListTile(leading: CircleAvatar(child: Icon(icon)), title: Text(title), subtitle: Text(subtitle), trailing: const Icon(Icons.open_in_new), onTap: onTap)));
}

class _SettingsPage extends StatelessWidget {
  const _SettingsPage({required this.midweekDay, required this.weekendDay, required this.onChanged});
  final int midweekDay, weekendDay; final void Function(int, int) onChanged;
  static const days = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [
    const Text('Dias das reuniões', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
    const SizedBox(height: 16),
    DropdownButtonFormField<int>(value: midweekDay, decoration: const InputDecoration(labelText: 'Reunião do meio de semana'), items: List.generate(7, (i) => DropdownMenuItem(value: i + 1, child: Text(days[i]))), onChanged: (v) => onChanged(v!, weekendDay)),
    const SizedBox(height: 14),
    DropdownButtonFormField<int>(value: weekendDay, decoration: const InputDecoration(labelText: 'Reunião do fim de semana'), items: List.generate(7, (i) => DropdownMenuItem(value: i + 1, child: Text(days[i]))), onChanged: (v) => onChanged(midweekDay, v!)),
    const SizedBox(height: 22),
    const Card(child: ListTile(leading: Icon(Icons.lock_outline), title: Text('Privacidade'), subtitle: Text('Designações e anotações ficam guardadas somente neste aparelho.'))),
  ]);
}

class _SectionTitle extends StatelessWidget { const _SectionTitle(this.text); final String text; @override Widget build(BuildContext context) => Text(text, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold)); }
class _EmptyCard extends StatelessWidget { const _EmptyCard(); @override Widget build(BuildContext context) => const Card(child: Padding(padding: EdgeInsets.all(18), child: Column(children: [Icon(Icons.event_note, size: 38), SizedBox(height: 8), Text('Nenhuma designação cadastrada.', style: TextStyle(fontWeight: FontWeight.bold)), SizedBox(height: 4), Text('Toque em “Designação” para criar seu primeiro plano.')]))); }
