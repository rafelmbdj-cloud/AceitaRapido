# Corrida Falada

Aplicativo Android que lê uma oferta por acessibilidade e, quando necessário,
usa captura autorizada e OCR em memória. Calcula o valor por quilômetro, mostra
uma faixa colorida, toca alertas diferentes e fala o bairro ou a rua.
**Não executa cliques e não aceita nem recusa corridas.**
Não é necessário selecionar o pacote do aplicativo de corridas: uma oferta é
identificada automaticamente pela combinação de valor, quilômetros e botão de aceitar.

- Excelente (verde): acima de R$ 3,00/km
- Boa (amarela): de R$ 2,00 a R$ 3,00/km
- Ruim (vermelha): abaixo de R$ 2,00/km

O parser possui teste automatizado com o texto extraído do print de referência,
incluindo R$ 23,08, coleta a 1,6 km, Rua Dois e Rua Nossa Senhora de Fátima.

Use primeiro com o aparelho parado, em modo de teste e sob supervisão de um
motorista adulto habilitado. Se o bairro não estiver escrito na oferta, o app
fala a rua para não inventar uma localização.
