CREATE TABLE "public"."t_file_info" (
  "id" int8 NOT NULL DEFAULT nextval('t_file_info_id_seq'::regclass),
  "device_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "user_id" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "file_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "file_path" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "access_url" varchar(512) COLLATE "pg_catalog"."default",
  "domain_prefix" varchar(128) COLLATE "pg_catalog"."default",
  "file_size" int8,
  "file_type" varchar(32) COLLATE "pg_catalog"."default",
  "file_md5" varchar(64) COLLATE "pg_catalog"."default",
  "record_date" date,
  "record_start_time" timestamp(6),
  "record_duration" int8,
  "upload_time" timestamp(6),
  "status" int4 DEFAULT 0,
  "create_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "update_time" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "deleted" int4 DEFAULT 0,
  CONSTRAINT "t_file_info_pkey" PRIMARY KEY ("id")
)
;

ALTER TABLE "public"."t_file_info" 
  OWNER TO "post";

CREATE INDEX "idx_file_info_device_id" ON "public"."t_file_info" USING btree (
  "device_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

CREATE INDEX "idx_file_info_file_hash" ON "public"."t_file_info" USING btree (
  "file_md5" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

CREATE INDEX "idx_file_info_record_date" ON "public"."t_file_info" USING btree (
  "record_date" "pg_catalog"."date_ops" ASC NULLS LAST
);

CREATE INDEX "idx_file_info_user_id" ON "public"."t_file_info" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

COMMENT ON COLUMN "public"."t_file_info"."id" IS '主键ID';

COMMENT ON COLUMN "public"."t_file_info"."device_id" IS '设备ID';

COMMENT ON COLUMN "public"."t_file_info"."user_id" IS '用户ID';

COMMENT ON COLUMN "public"."t_file_info"."file_name" IS '文件名';

COMMENT ON COLUMN "public"."t_file_info"."file_path" IS '文件存储路径';

COMMENT ON COLUMN "public"."t_file_info"."access_url" IS '文件访问URL';

COMMENT ON COLUMN "public"."t_file_info"."domain_prefix" IS '域名前缀';

COMMENT ON COLUMN "public"."t_file_info"."file_size" IS '文件大小(字节)';

COMMENT ON COLUMN "public"."t_file_info"."file_type" IS '文件类型';

COMMENT ON COLUMN "public"."t_file_info"."file_md5" IS '文件MD5值';

COMMENT ON COLUMN "public"."t_file_info"."record_date" IS '录音日期';

COMMENT ON COLUMN "public"."t_file_info"."record_start_time" IS '录音开始时间';

COMMENT ON COLUMN "public"."t_file_info"."record_duration" IS '录音时长(毫秒)';

COMMENT ON COLUMN "public"."t_file_info"."upload_time" IS '上传时间';

COMMENT ON COLUMN "public"."t_file_info"."status" IS '文件状态: 0-上传中，1-已完成，2-已失效';

COMMENT ON COLUMN "public"."t_file_info"."create_time" IS '创建时间';

COMMENT ON COLUMN "public"."t_file_info"."update_time" IS '更新时间';

COMMENT ON COLUMN "public"."t_file_info"."deleted" IS '是否删除: 0-未删除，1-已删除';

COMMENT ON TABLE "public"."t_file_info" IS '文件信息表';