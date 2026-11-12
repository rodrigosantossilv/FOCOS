# FOCOS — Contador de peças com YOLOv8 (em desenvolvimento)

**Ainda não há APK YOLOv8 pronto.** A integração Android utiliza ONNX Runtime, mas falta o modelo **treinado nas peças reais**. A automação do GitHub **não publica APK** enquanto o modelo estiver ausente.

## Classes (ordem obrigatória)

0. screw — parafuso
1. nut — porca
2. washer — arruela
3. automotive_part — peça automotiva

A classe automotiva é ampla: recomenda-se subdividi-la em classes concretas (filtro, sensor, conector etc.) quando houver fotos suficientes. Não use pesos COCO genéricos para prometer reconhecimento de peças.

## Dataset necessário

Comece com pelo menos 100–200 fotos **por classe** para um protótipo; varie modelos, tamanhos, fundos, sombras, ângulos, densidade e objetos encostados. Para boa confiabilidade podem ser necessárias centenas ou milhares de imagens por classe. Anote **cada objeto separadamente** com uma caixa delimitadora, inclusive quando encostados. Separe treino/validação/teste por sessão fotográfica, evitando imagens quase iguais entre conjuntos.

Formato YOLO: `images/train`, `images/val`, `images/test` e diretórios `labels/` correspondentes; cada linha de rótulo é `class_id x_center y_center width height` normalizados de 0 a 1.

Exemplo `data.yaml`:

```yaml
path: /caminho/dataset
train: images/train
val: images/val
test: images/test
names:
  0: screw
  1: nut
  2: washer
  3: automotive_part
```

## Treinamento no computador (Python)

```bash
pip install ultralytics
yolo detect train model=yolov8n.pt data=data.yaml imgsz=640 epochs=100
yolo export model=runs/detect/train/weights/best.pt format=onnx imgsz=640 opset=12 simplify=True
```

Confirme que o arquivo exportado possui **entrada float32 [1,3,640,640]** e **saída [1,8,N]** (4 coordenadas + 4 classes, sem NMS embutido). A integração não aceita YOLOv8 segmentation, modelo com NMS embutido ou modelo genérico de 80 classes. Copie o arquivo para:

`app/src/main/assets/parts_yolov8.onnx`

**Não adicione um arquivo falso/placeholder.** Teste o modelo em imagens de validação, confira precisão/recall, erros de contagem e tempo de inferência no celular antes de publicar.

## Arquitetura

- Kotlin, Android 8+ e ONNX Runtime local; não há chamada de inferência a servidor.
- Pré-processamento letterbox 640x640, RGB CHW normalizado.
- Pós-processamento: confiança, caixas, conversão de coordenadas e NMS por classe.
- Contagem manual por toque permanece disponível.
- A captura de foto utiliza a câmera do Android; nenhuma imagem é enviada pelo aplicativo.

## Compilação e publicação

O workflow `.github/workflows/android-apk.yml` é de **validação**, não de publicação. Somente após incluir o modelo treinado, testar inferência real no aparelho e validar os resultados deve-se habilitar publicação em Releases.

Para testes locais: abra o projeto no Android Studio e execute `gradle :app:assembleDebug` após instalar o modelo.
