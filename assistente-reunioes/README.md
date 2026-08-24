# Assistente de Reuniões

Aplicativo Android em Flutter para organizar a preparação pessoal das reuniões.

## Recursos da versão 1

- painel semanal com progresso;
- reunião do meio de semana, Estudo de A Sentinela e saída de campo;
- cadastro de designações e plano diário automático;
- anotações e marcação das etapas concluídas;
- ensaio com cronômetro;
- atalhos para JW.org e JW Library;
- dados guardados somente no aparelho.

O aplicativo não é oficial nem afiliado às Testemunhas de Jeová. Ele não copia publicações: organiza informações pessoais e abre links oficiais.

## Executar

```bash
flutter create . --platforms=android
flutter pub get
flutter run
```

## Gerar APK

```bash
flutter build apk --release
```

O APK será criado em `build/app/outputs/flutter-apk/app-release.apk`.
