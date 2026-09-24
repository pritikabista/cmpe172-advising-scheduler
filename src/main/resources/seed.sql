INSERT INTO users (username, password_hash, full_name, email, role) VALUES
('advisor_kim',   '$2a$10$gvZt5SdI2E5lfQ/iyLqJ6OoHBRUeFkNDBcq9UzCCemCJ4ddSeg/6m', 'Dr. Sarah Kim', 'sarah.kim@sjsu.edu',   'PROVIDER'),
('advisor_patel', '$2a$10$gvZt5SdI2E5lfQ/iyLqJ6OoHBRUeFkNDBcq9UzCCemCJ4ddSeg/6m', 'Raj Patel',     'raj.patel@sjsu.edu',   'PROVIDER'),
('student_alex',  '$2a$10$gvZt5SdI2E5lfQ/iyLqJ6OoHBRUeFkNDBcq9UzCCemCJ4ddSeg/6m', 'Alex Nguyen',   'alex.nguyen@sjsu.edu', 'CUSTOMER'),
('student_maya',  '$2a$10$gvZt5SdI2E5lfQ/iyLqJ6OoHBRUeFkNDBcq9UzCCemCJ4ddSeg/6m', 'Maya Johnson',  'maya.j@sjsu.edu',      'CUSTOMER'),
('student_leo',   '$2a$10$gvZt5SdI2E5lfQ/iyLqJ6OoHBRUeFkNDBcq9UzCCemCJ4ddSeg/6m', 'Leo Martinez',  'leo.m@sjsu.edu',       'CUSTOMER');

INSERT INTO providers (user_id, title, department, office_location) VALUES
(1, 'Academic Advisor', 'Computer Science',     'MacQuarrie Hall 215'),
(2, 'Academic Advisor', 'Computer Engineering', 'Engineering 284');

INSERT INTO services (name, description, duration_minutes, price) VALUES
('Registration Help',  'Help planning and registering for classes', 30, 0.00),
('Major Change',       'Discuss requirements for changing majors',  45, 0.00),
('Graduation Check',   'Review remaining graduation requirements',  30, 0.00),
('Probation Advising', 'Academic probation support and planning',   60, 0.00);

INSERT INTO availability_slots (provider_id, service_id, start_time, end_time) VALUES
(1, 1, CURRENT_DATE + INTERVAL '1 day 9 hours',   CURRENT_DATE + INTERVAL '1 day 9 hours 30 minutes'),
(1, 3, CURRENT_DATE + INTERVAL '1 day 10 hours',  CURRENT_DATE + INTERVAL '1 day 10 hours 30 minutes'),
(1, 2, CURRENT_DATE + INTERVAL '2 days 13 hours', CURRENT_DATE + INTERVAL '2 days 13 hours 45 minutes'),
(1, 4, CURRENT_DATE + INTERVAL '3 days 14 hours', CURRENT_DATE + INTERVAL '3 days 15 hours'),
(2, 1, CURRENT_DATE + INTERVAL '1 day 11 hours',  CURRENT_DATE + INTERVAL '1 day 11 hours 30 minutes'),
(2, 1, CURRENT_DATE + INTERVAL '2 days 9 hours',  CURRENT_DATE + INTERVAL '2 days 9 hours 30 minutes'),
(2, 3, CURRENT_DATE + INTERVAL '2 days 15 hours', CURRENT_DATE + INTERVAL '2 days 15 hours 30 minutes'),
(2, 2, CURRENT_DATE + INTERVAL '4 days 10 hours', CURRENT_DATE + INTERVAL '4 days 10 hours 45 minutes');

INSERT INTO appointments (customer_id, slot_id, service_id, status, notes) VALUES
(3, 1, 1, 'BOOKED', 'Need help picking CS electives');