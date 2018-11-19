CREATE TABLE m_object_collection (
  name_norm VARCHAR2(255 CHAR),
  name_orig VARCHAR2(255 CHAR),
  oid       VARCHAR2(36 CHAR) NOT NULL,
  PRIMARY KEY (oid)
) INITRANS 30;

CREATE INDEX iObjectCollectionNameOrig
  ON m_object_collection (name_orig) INITRANS 30;
ALTER TABLE m_object_collection
  ADD CONSTRAINT uc_object_collection_name UNIQUE (name_norm);

ALTER TABLE m_object_collection
  ADD CONSTRAINT fk_object_collection FOREIGN KEY (oid) REFERENCES m_object;

ALTER TABLE m_acc_cert_campaign ADD iteration NUMBER(10, 0) DEFAULT 1 NOT NULL;
ALTER TABLE m_acc_cert_case ADD iteration NUMBER(10, 0) DEFAULT 1 NOT NULL;
ALTER TABLE m_acc_cert_wi ADD iteration NUMBER(10, 0) DEFAULT 1 NOT NULL;

CREATE TABLE m_global_metadata (
  name  VARCHAR2(255 CHAR) NOT NULL,
  value VARCHAR2(255 CHAR),
  PRIMARY KEY (name)
) INITRANS 30;

INSERT INTO m_global_metadata VALUES ('databaseSchemaVersion', '3.9');
