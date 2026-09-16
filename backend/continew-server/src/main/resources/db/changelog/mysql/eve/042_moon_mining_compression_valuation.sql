-- liquibase formatted sql

-- changeset eve-corp-manager:042-moon-mining-compression-valuation
-- comment 固化月矿原矿与高密度压缩矿的官方类型映射，供开采统计按压缩后价值估算
CREATE TABLE `eve_mining_compression_mapping`
(
    `raw_type_id`        int                  NOT NULL COMMENT '月矿原矿类型ID',
    `compressed_type_id` int                  NOT NULL COMMENT '对应高密度压缩矿类型ID',
    `compression_ratio`  smallint unsigned    NOT NULL DEFAULT 100 COMMENT '原矿压缩为一单位压缩矿所需数量',
    `create_time`        datetime             NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        datetime             NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`raw_type_id`),
    UNIQUE KEY `uk_eve_mining_compression_compressed` (`compressed_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月矿原矿与高密度压缩矿类型映射';

INSERT INTO `eve_mining_compression_mapping` (`raw_type_id`, `compressed_type_id`, `compression_ratio`) VALUES
    (45490, 62463, 100), (45491, 62460, 100), (45492, 62454, 100), (45493, 62457, 100),
    (45494, 62474, 100), (45495, 62471, 100), (45496, 62477, 100), (45497, 62468, 100),
    (45498, 62483, 100), (45499, 62486, 100), (45500, 62489, 100), (45501, 62480, 100),
    (45502, 62492, 100), (45503, 62501, 100), (45504, 62498, 100), (45506, 62495, 100),
    (45510, 62510, 100), (45511, 62507, 100), (45512, 62504, 100), (45513, 62513, 100),
    (46280, 62464, 100), (46281, 62467, 100), (46282, 62461, 100), (46283, 62466, 100),
    (46284, 62455, 100), (46285, 62456, 100), (46286, 62458, 100), (46287, 62459, 100),
    (46288, 62475, 100), (46289, 62476, 100), (46290, 62472, 100), (46291, 62473, 100),
    (46292, 62478, 100), (46293, 62479, 100), (46294, 62469, 100), (46295, 62470, 100),
    (46296, 62484, 100), (46297, 62485, 100), (46298, 62487, 100), (46299, 62488, 100),
    (46300, 62490, 100), (46301, 62491, 100), (46302, 62481, 100), (46303, 62482, 100),
    (46304, 62493, 100), (46305, 62494, 100), (46306, 62502, 100), (46307, 62503, 100),
    (46308, 62499, 100), (46309, 62500, 100), (46310, 62496, 100), (46311, 62497, 100),
    (46312, 62511, 100), (46313, 62512, 100), (46314, 62508, 100), (46315, 62509, 100),
    (46316, 62505, 100), (46317, 62506, 100), (46318, 62514, 100), (46319, 62515, 100);
