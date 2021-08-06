# on-device-face-mask-detector
# Used tools
+ CameraX a Jetpack support library, it facilitates using the camera and anlayzing frames
+ ML Kit's face detection API
+ Tensorflow lite : an open source deep learning framework for on-device inference.
# Tflite model
To build the Image Classifier I used the following dataset from Kaggle : [dataset](https://www.kaggle.com/ashishjangra27/face-mask-12k-images-dataset)
# Screen Shots
## Faces detection
  The following screen shot shows the first step : Faces detection using Ml kit
  
  
![Faces detection](https://github.com/Issamoh/on-device-face-mask-detector/blob/main/screenshots/1.jpg)
## Mask detection
  The detected faces are passed to the tflite model to detect if the face is wearing a mask or not
  
  
![Mask detection](https://github.com/Issamoh/on-device-face-mask-detector/blob/main/screenshots/2.jpg)
![Mask detection](https://github.com/Issamoh/on-device-face-mask-detector/blob/main/screenshots/3.jpg)
