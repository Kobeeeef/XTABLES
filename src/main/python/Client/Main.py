from Client.XTablesClient import XTablesClient

client = XTablesClient("localhost", 1735)
client.put_integer("ok", 1).complete()

