require 'test/unit.rb'
require 'sling/sling'
require 'sling/users'
require 'sling/sites'
require 'sling/search'
require 'tempfile'

class SlingTest < Test::Unit::TestCase

  def setup
    @s = SlingInterface::Sling.new()
    @um = SlingUsers::UserManager.new(@s)
    @sm = SlingSites::SiteManager.new(@s)
    @search = SlingSearch::SearchManager.new(@s)
    @created_nodes = []
    @created_users = []
    @created_groups = []
    @created_sites = []
  end

  def teardown
    @s.switch_user(SlingUsers::User.admin_user)
    @created_nodes.reverse.each { |n| assert(@s.delete_node(n), "Expected node delete to succeed") }
    @created_users.each { |u| assert(@um.delete_user(u.name), "Expected user delete to succeed") }
    @created_groups.each { |g| assert(@um.delete_group(g), "Expected group delete to succeed") }
    @created_sites.each { |s| assert(@sm.delete_site(s), "Expected site delete to succeed") }
  end

  def create_node(path, props={})
    puts "Path is #{path}"
    res = @s.create_node(path, props)
    assert_not_equal("500", res.code, "Expected to be able to create node")
    @created_nodes << path
    return path
  end

  def create_file_node(path, fieldname, filename, data, content_type="text/plain")
    res = @s.create_file_node(path, fieldname, filename, data, content_type)
    @created_nodes << path unless @created_nodes.include?(path)
    return res
  end

  def create_user(username)
    u = @um.create_user(username)
    assert_not_nil(u, "Expected user to be created: #{username}")
    @created_users << u
    return u
  end
 
  def create_group(groupname)
    g = @um.create_group(groupname)
    assert_not_nil(g, "Expected group to be created: #{groupname}")
    @created_groups << groupname
    return g
  end

  def create_site(path,title,id)
    s = @sm.create_site(path,title,id)
    assert_not_nil(s, "Expected site to be created: #{path}")
    @created_sites << (path+id)
    return s
  end

end

