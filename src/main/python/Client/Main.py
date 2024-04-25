from Client.XTablesClient import XTablesClient

client = XTablesClient("localhost", 1735)

r = client.put_integer("ok", 12).complete()

print(r)
