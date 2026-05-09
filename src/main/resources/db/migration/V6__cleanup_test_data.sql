-- Clean up test data inserted in previous migrations
DELETE FROM api_keys WHERE producer IN ('test-tenant-1', 'test-tenant-2');

-- Note: Keep test data for specific functionality testing 
-- Override in environment profiles (test, dev) to repopulate as needed
