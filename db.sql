-- MySQL Database: company_clean_hub
-- phpMyAdmin SQL Dump

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";

-- Database: `company_clean_hub`
CREATE DATABASE IF NOT EXISTS `company_clean_hub` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `company_clean_hub`;

--
-- Table structure for table `assignments`
--

DROP TABLE IF EXISTS `assignments`;
CREATE TABLE `assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `salary_at_time` decimal(38,2) DEFAULT NULL,
  `start_date` date NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `work_days` int DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `assignment_type` enum('REGULAR','TEMPORARY') DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr3prpg0i2x7w57npako9tx2gw` (`customer_id`),
  KEY `FKr69edfcloedr3t1ylmiesklif` (`employee_id`),
  CONSTRAINT `FKr3prpg0i2x7w57npako9tx2gw` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKr69edfcloedr3t1ylmiesklif` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `assignments`
--

INSERT INTO `assignments` VALUES (1,'2025-11-24 16:31:06.926114','Phân công nhân viên dọn dẹp',15000000.00,'2024-01-01','ACTIVE','2025-11-24 16:31:06.926114',22,10,7,'REGULAR'),(3,'2025-11-25 13:25:14.326915','Điều động',250000.00,'2025-11-25','ACTIVE','2025-11-25 13:25:14.326915',20,8,9,'REGULAR'),(5,'2025-11-26 23:00:38.451771','Điều động John Doe đến Công ty ABCD',11000000.00,'2025-11-26','ACTIVE','2025-11-26 23:00:38.451771',20,8,7,'REGULAR'),(8,'2025-11-27 10:19:33.937324','Doe ốm',20000.00,'2025-11-27','ACTIVE','2025-11-27 10:19:33.937324',1,10,9,'TEMPORARY');

--
-- Table structure for table `attendance`
--

DROP TABLE IF EXISTS `attendance`;
CREATE TABLE `attendance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bonus` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `date` date NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_overtime` bit(1) DEFAULT NULL,
  `overtime_amount` decimal(38,2) DEFAULT NULL,
  `penalty` decimal(38,2) DEFAULT NULL,
  `support_cost` decimal(38,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `work_hours` decimal(38,2) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `assignment_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmus0iwaah6rge8pd0q20kpuu1` (`approved_by`),
  KEY `FK5wq4b8kfqq46isym6mum9l86p` (`assignment_id`),
  KEY `FKb48lmkou5j4rvde9sr88bqgjw` (`employee_id`),
  CONSTRAINT `FK5wq4b8kfqq46isym6mum9l86p` FOREIGN KEY (`assignment_id`) REFERENCES `assignments` (`id`),
  CONSTRAINT `FKb48lmkou5j4rvde9sr88bqgjw` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`),
  CONSTRAINT `FKmus0iwaah6rge8pd0q20kpuu1` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

--
-- Dumping data for table `attendance`
--
INSERT INTO `attendance` VALUES (78,0.00,'2025-11-27 01:21:43.971596','2025-11-03',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.971596',8.00,NULL,1,7),(79,0.00,'2025-11-27 01:21:43.973928','2025-11-04',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.973928',8.00,NULL,1,7),(80,0.00,'2025-11-27 01:21:43.977892','2025-11-05',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.977892',8.00,NULL,1,7),(81,0.00,'2025-11-27 01:21:43.977892','2025-11-06',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.977892',8.00,NULL,1,7),(82,0.00,'2025-11-27 01:21:43.983824','2025-11-07',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.983824',8.00,NULL,1,7),(83,0.00,'2025-11-27 01:21:43.985844','2025-11-08',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.985844',8.00,NULL,1,7),(84,0.00,'2025-11-27 01:21:43.990058','2025-11-10',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.990058',8.00,NULL,1,7),(85,0.00,'2025-11-27 01:21:43.995545','2025-11-11',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.995545',8.00,NULL,1,7),(86,0.00,'2025-11-27 01:21:43.995545','2025-11-12',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.995545',8.00,NULL,1,7),(87,0.00,'2025-11-27 01:21:43.998631','2025-11-13',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.998631',8.00,NULL,1,7),(88,0.00,'2025-11-27 01:21:43.998631','2025-11-14',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:43.998631',8.00,NULL,1,7),(89,0.00,'2025-11-27 01:21:44.011703','2025-11-17',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.011703',8.00,NULL,1,7),(90,0.00,'2025-11-27 01:21:44.014316','2025-11-18',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.014316',8.00,NULL,1,7),(91,0.00,'2025-11-27 01:21:44.018829','2025-11-19',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.018829',8.00,NULL,1,7),(92,0.00,'2025-11-27 01:21:44.018829','2025-11-20',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.018829',8.00,NULL,1,7),(93,0.00,'2025-11-27 01:21:44.024274','2025-11-21',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.024274',8.00,NULL,1,7),(94,0.00,'2025-11-27 01:21:44.024274','2025-11-22',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.024274',8.00,NULL,1,7),(95,0.00,'2025-11-27 01:21:44.035359','2025-11-24',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.035359',8.00,NULL,1,7),(96,0.00,'2025-11-27 01:21:44.038010','2025-11-25',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.038010',8.00,NULL,1,7),(99,0.00,'2025-11-27 01:21:44.046150','2025-11-28',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.046150',8.00,NULL,1,7),(100,0.00,'2025-11-27 01:21:44.050366','2025-11-29',NULL,0,0.00,0.00,0.00,'2025-11-27 01:21:44.050366',8.00,NULL,1,7),(101,0.00,'2025-11-27 01:22:09.394826','2025-11-26','Điều động tạm thời',0,0.00,0.00,0.00,'2025-11-27 01:22:09.394826',8.00,NULL,1,7),(103,0.00,'2025-11-27 10:19:34.237941','2025-11-27','Doe ốm',0,0.00,0.00,0.00,'2025-11-27 10:19:34.237941',8.00,NULL,8,9);

--
-- Table structure for table `contract_services`
--

DROP TABLE IF EXISTS `contract_services`;


CREATE TABLE `contract_services` (
  `contract_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  PRIMARY KEY (`contract_id`,`service_id`),
  KEY `FK712cmy0mue4wfk1vfw4b18kw7` (`service_id`),
  CONSTRAINT `FK712cmy0mue4wfk1vfw4b18kw7` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`),
  CONSTRAINT `FKj5amvvhuo35k4sjg6f9mvo7w7` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `contract_services`
--



INSERT INTO `contract_services` VALUES (1,1),(1,2);



--
-- Table structure for table `contracts`
--

DROP TABLE IF EXISTS `contracts`;


CREATE TABLE `contracts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `base_price` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `discount_cost` decimal(38,2) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `extra_cost` decimal(38,2) DEFAULT NULL,
  `final_price` decimal(38,2) DEFAULT NULL,
  `payment_status` varchar(50) DEFAULT NULL,
  `start_date` date NOT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `vat` decimal(38,2) DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgcu7bfqv1j7nltm5uhk91kxcy` (`customer_id`),
  CONSTRAINT `FKgcu7bfqv1j7nltm5uhk91kxcy` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `contracts`
--



INSERT INTO `contracts` VALUES (1,10000000.00,'2025-11-23 01:16:51.385449','Hợp đồng dọn dẹp văn phòng',500000.00,'2024-12-31',0.00,10500000.00,'PENDING','2024-01-01',11000000.00,'2025-11-23 01:16:51.385449',1000000.00,8);



--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;


CREATE TABLE `customers` (
  `address` varchar(255) DEFAULT NULL,
  `company` varchar(255) DEFAULT NULL,
  `contact_info` varchar(255) DEFAULT NULL,
  `customer_code` varchar(50) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `tax_code` varchar(100) DEFAULT NULL,
  `id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FKpog72rpahj62h7nod9wwc28if` FOREIGN KEY (`id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `customers`
--



INSERT INTO `customers` VALUES ('Hà Nội','ABC Corp','Ms Lan 0909888999','CUST001','Khách VIP','Công ty ABCD','0123456789',8),('2132','21321','132','2132','1322','32132','132',10);



--
-- Table structure for table `employee_images`
--

DROP TABLE IF EXISTS `employee_images`;


CREATE TABLE `employee_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_path` varchar(512) NOT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbogj7u3rkdwr4q4cf63bg0mxi` (`employee_id`),
  CONSTRAINT `FKbogj7u3rkdwr4q4cf63bg0mxi` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `employee_images`
--






--
-- Table structure for table `employees`
--

DROP TABLE IF EXISTS `employees`;


CREATE TABLE `employees` (
  `address` varchar(255) DEFAULT NULL,
  `allowance` decimal(38,2) DEFAULT NULL,
  `bank_account` varchar(255) DEFAULT NULL,
  `bank_name` varchar(255) DEFAULT NULL,
  `base_salary` decimal(38,2) DEFAULT NULL,
  `cccd` varchar(50) NOT NULL,
  `daily_salary` decimal(38,2) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `employee_code` varchar(50) NOT NULL,
  `employment_type` enum('FIXED_BY_CONTRACT','FIXED_BY_DAY','TEMPORARY') NOT NULL,
  `health_insurance` decimal(38,2) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `social_insurance` decimal(38,2) DEFAULT NULL,
  `id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKetqhw9qqnad1kyjq3ks1glw8x` (`employee_code`),
  CONSTRAINT `FKd6th9xowehhf1kmmq1dsseq28` FOREIGN KEY (`id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `employees`
--



INSERT INTO `employees` VALUES ('Hanoi ',NULL,'23221322',NULL,11000000.00,'012345678',410000.00,'Cập nhật thông tin','NV111','FIXED_BY_DAY',210000.00,'John Doe',820000.00,7),('dss',NULL,'2322132',NULL,2323213.00,'223223213',232132.00,'','NV002','TEMPORARY',321321.00,'đại',321321.00,9);



--
-- Table structure for table `material_distributions`
--

DROP TABLE IF EXISTS `material_distributions`;


CREATE TABLE `material_distributions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `date` date DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `price_at_time` decimal(38,2) DEFAULT NULL,
  `quantity` decimal(38,2) DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `manager_id` bigint DEFAULT NULL,
  `material_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKincye59e6d73c9mcgxgbpino2` (`employee_id`),
  KEY `FK1mkjawpt7lmnmpw72a4omc4vp` (`manager_id`),
  KEY `FKqaf1a78r2ewf71j3ltndkwodp` (`material_id`),
  CONSTRAINT `FK1mkjawpt7lmnmpw72a4omc4vp` FOREIGN KEY (`manager_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKincye59e6d73c9mcgxgbpino2` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`),
  CONSTRAINT `FKqaf1a78r2ewf71j3ltndkwodp` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `material_distributions`
--






--
-- Table structure for table `materials`
--

DROP TABLE IF EXISTS `materials`;


CREATE TABLE `materials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `unit` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `materials`
--






--
-- Table structure for table `payrolls`
--

DROP TABLE IF EXISTS `payrolls`;


CREATE TABLE `payrolls` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `advance_total` decimal(38,2) DEFAULT NULL,
  `allowance_total` decimal(38,2) DEFAULT NULL,
  `bonus_total` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `final_salary` decimal(38,2) DEFAULT NULL,
  `insurance_total` decimal(38,2) DEFAULT NULL,
  `is_paid` bit(1) DEFAULT NULL,
  `payment_date` datetime(6) DEFAULT NULL,
  `penalty_total` decimal(38,2) DEFAULT NULL,
  `salary_base` decimal(38,2) DEFAULT NULL,
  `total_days` int DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `accountant_id` bigint DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5nftlwo6ypbbjc341luikl0a2` (`accountant_id`),
  KEY `FKiyfp8uysuhgfkod3xcdhjm7qf` (`employee_id`),
  CONSTRAINT `FK5nftlwo6ypbbjc341luikl0a2` FOREIGN KEY (`accountant_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKiyfp8uysuhgfkod3xcdhjm7qf` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `payrolls`
--



INSERT INTO `payrolls` VALUES (1,0.00,50000.00,100000.00,'2025-11-26 20:40:12.549027',14120000.00,1030000.00,0,NULL,0.00,15000000.00,1,'2025-11-26 20:40:12.549027',NULL,7);



--
-- Table structure for table `ratings`
--

DROP TABLE IF EXISTS `ratings`;


CREATE TABLE `ratings` (
  `id` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `customer_id` bigint DEFAULT NULL,
  `service_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb0aai9hp9gtd2don8c08qxn3y` (`customer_id`),
  KEY `FK5lu9nh6g1l7vhogjj8h2qydyb` (`service_id`),
  CONSTRAINT `FK5lu9nh6g1l7vhogjj8h2qydyb` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`),
  CONSTRAINT `FKb0aai9hp9gtd2don8c08qxn3y` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `ratings`
--






--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;


CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `roles`
--



INSERT INTO `roles` VALUES (1,'CUSTOMER','2025-11-23 01:06:16.412326','Khách hàng','CUSTOMER','2025-11-23 01:08:13.315156'),(2,'EMPLOYEE','2025-11-23 01:08:39.451406','Nhân viên','EMPLOYEE','2025-11-23 01:08:39.451406'),(3,'QLT1','2025-11-23 01:08:48.383541','Quản lý tổng 1','QLT1','2025-11-23 01:08:48.383541'),(4,'QLT2','2025-11-23 01:08:52.758139','Quản lý tổng 2','QLT2','2025-11-23 01:08:52.758139'),(5,'QLV','2025-11-23 01:08:57.693777','Quản lý vùng','QLV','2025-11-23 01:08:57.693777'),(6,'ACCOUNTANT','2025-11-23 01:09:02.684533','Kế toán','ACCOUNTANT','2025-11-23 01:09:02.684533');



--
-- Table structure for table `service_images`
--

DROP TABLE IF EXISTS `service_images`;


CREATE TABLE `service_images` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_path` varchar(512) NOT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `service_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKico1fuyxgk2m2tfqt12tj4kh2` (`service_id`),
  CONSTRAINT `FKico1fuyxgk2m2tfqt12tj4kh2` FOREIGN KEY (`service_id`) REFERENCES `services` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `service_images`
--






--
-- Table structure for table `services`
--

DROP TABLE IF EXISTS `services`;


CREATE TABLE `services` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `main_image` varchar(255) DEFAULT NULL,
  `price_from` decimal(38,2) DEFAULT NULL,
  `price_to` decimal(38,2) DEFAULT NULL,
  `status` varchar(50) NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `services`
--



INSERT INTO `services` VALUES (1,'2025-11-23 01:15:54.456097','Dịch vụ dọn dẹp văn phòng chuyên nghiệp','https://example.com/image.jpg',500000.00,2000000.00,'ACTIVE','Dọn dẹp trường','2025-11-23 21:40:54.757399'),(2,'2025-11-23 01:16:17.986442','Dịch vụ dọn dẹp văn phòng chuyên nghiệp','https://example.com/image.jpg',500000.00,2000000.00,'ACTIVE','Dọn dẹp trường','2025-11-23 01:16:17.986442'),(3,'2025-11-23 21:41:06.152825','1','1',1.00,111.00,'ACTIVE','11','2025-11-23 21:41:06.152825'),(4,'2025-11-23 21:41:45.383403','1','1',1.00,10.00,'ACTIVE','1','2025-11-23 21:41:45.383403');



--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;


CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(50) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKp56c1712k691lhsyewcssf40f` (`role_id`),
  CONSTRAINT `FKp56c1712k691lhsyewcssf40f` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


--
-- Dumping data for table `users`
--



INSERT INTO `users` VALUES (1,'2025-11-23 01:10:35.029925','customer01@gmail.com','$2a$10$1SWBZ1kZ/jWiWFxuQ481POcIfY7VD4SSAJSsV6rxr2qSaPLraMjbe','0909000001','ACTIVE','2025-11-23 01:10:35.029925','customer01',1),(2,'2025-11-23 01:10:49.120348','employee01@gmail.com','$2a$10$7UpYZGKDi/NrfqDR0fxUB./Q.2sNwVTf5/cehUqLhxh3uiGU6OuCK','0909000002','ACTIVE','2025-11-23 01:10:49.120348','employee01',2),(3,'2025-11-23 01:10:53.684568','qlt1@gmail.com','$2a$10$PTikg2iDunjD6ICYm4kw1uPSN9FQasT5tJGX/AN4683Unw5Mvot/i','0909000003','ACTIVE','2025-11-23 01:10:53.684568','qlt1_user',3),(4,'2025-11-23 01:10:58.601647','qlt2@gmail.com','$2a$10$RAOS6g4neh8iwvcofJYfluvZNHzMJC8yKPxAAJDM1QeU3wOYzCrdG','0909000004','ACTIVE','2025-11-23 01:10:58.601647','qlt2_user',4),(5,'2025-11-23 01:11:03.160086','qlv@gmail.com','$2a$10$bGiDjvUEunN0loBwGRE8PedH9gCuuRyxiUx0TUIQBdjgXHbEWAobq','0909000005','ACTIVE','2025-11-23 01:11:03.160086','qlv_user',5),(6,'2025-11-23 01:11:08.145538','accountant01@gmail.com','$2a$10$eIEhiRhAN71IfR0rzt0RBelz0OzO8ZnLG1l0CbjWZIivxPWKxZV7O','0909000006','ACTIVE','2025-11-23 01:11:08.145538','accountant01',6),(7,NULL,'jdoe2@example.com','$2a$10$rJLHcI967ucQhZy8QBZPKOedxfyCPkuzeRo5siffhwrk1WWckwOPq','21323222','ACTIVE',NULL,'jdoe',2),(8,NULL,'kh01@gmail.com','$2a$10$Cabdkz1AQmYUWvxehsdVWOt/wxq0PDX3gZheb5TGmc5AXx2xRI/py','0909888999','ACTIVE',NULL,'khachhang01',1),(9,NULL,'dai@gmail.com','$2a$10$xlJE7xBiyfPJqCSwLT9LjOBzKXO2JFjQVqH9vtin3lAwlqshEPBgm','213232221','ACTIVE',NULL,'daile269',2),(10,NULL,'21221@gmail.com','$2a$10$ohQ.c9COIVJ920PWK04OCOYeHddv4slrrWqOfDxA1NcwtYV/8xDK6','232132213','ACTIVE',NULL,'kh1',1),(12,'2025-11-24 17:15:19.262046','22232@gmail.com','$2a$10$wcYaHHxC27KeGeMzh9yR/Oh091K/5Kxs8ZV25XRa/O66LeapSQu5C','12322',NULL,'2025-11-26 09:07:07.229023','122',3);


COMMIT;

-- Dump completed on 2025-11-27 14:39:40
