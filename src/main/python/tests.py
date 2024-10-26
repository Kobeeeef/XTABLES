import base64
import logging
import time

import cv2
from XTablesClient import XTablesClient

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    client = XTablesClient(useZeroMQ=True)
    client.

# if __name__ == "__main__":
#     logging.basicConfig(level=logging.INFO)
#     client = XTablesClient(useZeroMQ=True, server_port=1735)
#     while True:
#         client.executePutString("ok", "oko")

# if __name__ == "__main__":
#     logging.basicConfig(level=logging.INFO)
#     client = XTablesClient(useZeroMQ=True, server_port=1735)
#     cap = cv2.VideoCapture(0)
#
#     while cap.isOpened():
#         ret, frame = cap.read()
#         if not ret:
#             break
#
#         _, buffer = cv2.imencode('.jpg', frame)
#         encoded_frame = base64.b64encode(buffer).decode('utf-8')
#         client.push_frame("image", encoded_frame)
#
#         cv2.imshow("Sending Frame", frame)
#         if cv2.waitKey(1) & 0xFF == ord('q'):
#             break
#
#     cap.release()
#     cv2.destroyAllWindows()
