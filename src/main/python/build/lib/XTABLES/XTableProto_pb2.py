# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# NO CHECKED-IN PROTOBUF GENCODE
# source: XTableProto.proto
# Protobuf Python Version: 5.29.2
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import descriptor_pool as _descriptor_pool
from google.protobuf import runtime_version as _runtime_version
from google.protobuf import symbol_database as _symbol_database
from google.protobuf.internal import builder as _builder
_runtime_version.ValidateProtobufRuntimeVersion(
    _runtime_version.Domain.PUBLIC,
    5,
    29,
    2,
    '',
    'XTableProto.proto'
)
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor_pool.Default().AddSerializedFile(b'\n\x11XTableProto.proto\"\x88\t\n\rXTableMessage\x12\'\n\x07\x63ommand\x18\x01 \x01(\x0e\x32\x16.XTableMessage.Command\x12\x10\n\x03key\x18\x02 \x01(\tH\x00\x88\x01\x01\x12\x12\n\x05value\x18\x03 \x01(\x0cH\x01\x88\x01\x01\x12&\n\x04type\x18\x04 \x01(\x0e\x32\x13.XTableMessage.TypeH\x02\x88\x01\x01\x12\x0f\n\x02id\x18\x05 \x01(\x0cH\x03\x88\x01\x01\x1a\x8f\x01\n\tXTableLog\x12-\n\x05level\x18\x01 \x01(\x0e\x32\x1e.XTableMessage.XTableLog.Level\x12\x0f\n\x07message\x18\x02 \x01(\t\"B\n\x05Level\x12\x0b\n\x07UNKNOWN\x10\x00\x12\x08\n\x04INFO\x10\x01\x12\x0b\n\x07WARNING\x10\x02\x12\n\n\x06SEVERE\x10\x03\x12\t\n\x05\x46\x41TAL\x10\x04\x1a\xeb\x01\n\x0cXTableUpdate\x12\x0b\n\x03key\x18\x01 \x01(\t\x12\x36\n\x08\x63\x61tegory\x18\x02 \x01(\x0e\x32$.XTableMessage.XTableUpdate.Category\x12\r\n\x05value\x18\x03 \x01(\x0c\x12!\n\x04type\x18\x04 \x01(\x0e\x32\x13.XTableMessage.Type\"d\n\x08\x43\x61tegory\x12\x0b\n\x07UNKNOWN\x10\x00\x12\n\n\x06UPDATE\x10\x01\x12\n\n\x06\x44\x45LETE\x10\x02\x12\x0b\n\x07PUBLISH\x10\x03\x12\x0c\n\x08REGISTRY\x10\x04\x12\x0f\n\x0bINFORMATION\x10\x05\x12\x07\n\x03LOG\x10\x06\x1a\x92\x01\n\nClientInfo\x12\x12\n\nip_address\x18\x01 \x01(\t\x12\x10\n\x08hostname\x18\x02 \x01(\t\x12\x18\n\x10operating_system\x18\x03 \x01(\t\x12\x0c\n\x04port\x18\x04 \x01(\x05\x12\x11\n\tis_active\x18\x05 \x01(\x08\x12\x0f\n\x07version\x18\x06 \x01(\t\x12\x12\n\nuser_agent\x18\x07 \x01(\t\"y\n\x04Type\x12\x0b\n\x07UNKNOWN\x10\x00\x12\n\n\x06STRING\x10\x01\x12\n\n\x06\x44OUBLE\x10\x02\x12\t\n\x05INT64\x10\x05\x12\x08\n\x04\x42OOL\x10\x06\x12\t\n\x05\x42YTES\x10\x07\x12\x08\n\x04\x45NUM\x10\x08\x12\x0b\n\x07MESSAGE\x10\t\x12\t\n\x05\x41RRAY\x10\n\x12\n\n\x06OBJECT\x10\x0b\"\xbc\x02\n\x07\x43ommand\x12\x13\n\x0fUNKNOWN_COMMAND\x10\x00\x12\x07\n\x03PUT\x10\x01\x12\x07\n\x03GET\x10\x02\x12\t\n\x05\x44\x45\x42UG\x10\x03\x12\x0e\n\nGET_TABLES\x10\x04\x12\x0e\n\nRUN_SCRIPT\x10\x05\x12\x0e\n\nUPDATE_KEY\x10\x06\x12\n\n\x06\x44\x45LETE\x10\x07\x12\x0b\n\x07PUBLISH\x10\x08\x12\x14\n\x10SUBSCRIBE_DELETE\x10\t\x12\x16\n\x12UNSUBSCRIBE_DELETE\x10\n\x12\x16\n\x12UNSUBSCRIBE_UPDATE\x10\x0b\x12\x08\n\x04PING\x10\x0c\x12\x10\n\x0cGET_RAW_JSON\x10\r\x12\x10\n\x0c\x44\x45LETE_EVENT\x10\x0e\x12\x10\n\x0cUPDATE_EVENT\x10\x0f\x12\x0f\n\x0bINFORMATION\x10\x10\x12\x11\n\rREBOOT_SERVER\x10\x11\x12\x0c\n\x08REGISTRY\x10\x12\x42\x06\n\x04_keyB\x08\n\x06_valueB\x07\n\x05_typeB\x05\n\x03_idb\x06proto3')

_globals = globals()
_builder.BuildMessageAndEnumDescriptors(DESCRIPTOR, _globals)
_builder.BuildTopDescriptorsAndMessages(DESCRIPTOR, 'XTableProto_pb2', _globals)
if not _descriptor._USE_C_DESCRIPTORS:
  DESCRIPTOR._loaded_options = None
  _globals['_XTABLEMESSAGE']._serialized_start=22
  _globals['_XTABLEMESSAGE']._serialized_end=1182
  _globals['_XTABLEMESSAGE_XTABLELOG']._serialized_start=176
  _globals['_XTABLEMESSAGE_XTABLELOG']._serialized_end=319
  _globals['_XTABLEMESSAGE_XTABLELOG_LEVEL']._serialized_start=253
  _globals['_XTABLEMESSAGE_XTABLELOG_LEVEL']._serialized_end=319
  _globals['_XTABLEMESSAGE_XTABLEUPDATE']._serialized_start=322
  _globals['_XTABLEMESSAGE_XTABLEUPDATE']._serialized_end=557
  _globals['_XTABLEMESSAGE_XTABLEUPDATE_CATEGORY']._serialized_start=457
  _globals['_XTABLEMESSAGE_XTABLEUPDATE_CATEGORY']._serialized_end=557
  _globals['_XTABLEMESSAGE_CLIENTINFO']._serialized_start=560
  _globals['_XTABLEMESSAGE_CLIENTINFO']._serialized_end=706
  _globals['_XTABLEMESSAGE_TYPE']._serialized_start=708
  _globals['_XTABLEMESSAGE_TYPE']._serialized_end=829
  _globals['_XTABLEMESSAGE_COMMAND']._serialized_start=832
  _globals['_XTABLEMESSAGE_COMMAND']._serialized_end=1148
# @@protoc_insertion_point(module_scope)