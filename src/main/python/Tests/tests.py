
import logging
import time



if __name__ == "__main__":
    c = XTablesClient(server_port=1735, server_ip="localhost")
    # Usage example
    image_path = 'D:\\stuff\\IdeaProjects\\XTABLES\\src\\main\\python\\DSC_0841_1.jpg'
    image_bytes = image_to_bytes(image_path).replace(b'\n', b'')
    i = 0
    while True:
        c.executePutBytes("test", b'ad')
    #c.subscribe_to_key("test", consumer)
    time.sleep(1000000)
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
