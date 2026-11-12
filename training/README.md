# Treinar YOLOv8 para FOCOS

## Status
O aplicativo contém o runtime ONNX e a integração de inferência, mas **não existe modelo treinado anexado**. Nenhum APK YOLOv8 deve ser publicado até que o modelo seja treinado, exportado e validado no Android.

## Fotos necessárias
Fotografe as quatro classes (parafuso, porca, arruela, peça automotiva), em fundos claros/escuros, ângulos e iluminações diferentes. Inclua objetos isolados, encostados, parcialmente ocluídos e fotos sem peças (negativas). Para "peça automotiva", é preferível dividir por tipos concretos (filtro, rolamento, etc.) em vez de usar uma classe excessivamente ampla.

Anote cada objeto com bounding box (YOLO txt, class x_center y_center width height normalizados). Separe fotos por sessão em treino/validação/teste, evitando fotos quase idênticas em conjuntos diferentes. Comece com algumas centenas de exemplos por classe e aumente conforme os erros; isso não garante precisão.

## Treinamento no PC
```bash
pip install ultralytics
yolo detect train model=yolov8n.pt data=training/data.yaml imgsz=640 epochs=100
yolo export model=runs/detect/train/weights/best.pt format=onnx imgsz=640 opset=12 simplify=True nms=False
```

Copie `best.onnx` para `app/src/main/assets/parts_yolov8.onnx`.
A ordem das classes precisa ser: screw, nut, washer, automotive_part.
A saída esperada é `[1,8,N]`, sem NMS embutido, entrada float32 `[1,3,640,640]`.
Teste com fotos nunca vistas e compare contagem prevista e real antes de publicar.

## Privacidade
A inferência ocorre no dispositivo, sem upload de fotos. O app usa a câmera do sistema para capturar imagens no armazenamento do Android.
