# Protótipo de câmera — Contador de Peças
Versão experimental separada do aplicativo FOCOS existente.

- CameraX mostra imagem ao vivo.
- ML Kit detecta objetos genéricos e desenha caixas com quantidade.
- **Não reconhece códigos Mercedes-Benz e limita a detecção a 5 objetos.**
- Não é modelo treinado para peças e ainda requer teste em dispositivo físico.

Compilar com Gradle/Android Studio em `prototipo-camera-mlkit`. O workflow GitHub Actions tentará gerar um APK de depuração como artefato, sem substituir o APK do projeto principal.
