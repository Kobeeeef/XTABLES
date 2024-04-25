class RequestAction:
    def __init__(self, client, value, response_type=None):
        self.client = client
        self.value = value
        self.response_type = response_type

    def queue(self, on_success=None, on_failure=None):
        future = self.client.send_async(self.value)
        if on_success or on_failure:
            future.add_done_callback(lambda f: on_success(f.result()) if f.exception() is None else on_failure(f.exception()))

    def complete(self):
        return self.client.send_complete(self.value, self.response_type)