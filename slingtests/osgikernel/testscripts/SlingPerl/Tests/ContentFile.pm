#!/usr/bin/perl

package Tests::Content;

#{{{imports

use warnings;
use strict;
use Carp;
use lib qw ( .. );
use Sling::Content;
use Test::More;
use version; our $VERSION = qv('0.0.1');
use FileHandle;
use Digest::MD5 'md5_hex';

#}}}

#{{{sub run_regression_test
sub run_regression_test {
    my ( $authn, $verbose, $log ) = @_;
    my $test_file_path = '../data/test.jpg';
    my $test_file_name = 'test.jpg';
    my $test_file_dest = "blob_$$";

    # Sling content object:
    my $content = new Sling::Content( $authn, $verbose, $log );

    # Run tests:
    ok( defined $content,
        'Content Test: Sling Content Object successfully created.' );
    ok(
        $content->upload_file(
            $test_file_path, $test_file_dest, $test_file_name
        ),
        "Content Test: Content \"$test_file_name\" uploaded successfully."
    );
    ok( $content->exists($test_file_dest),
        "Content Test: Content \"$test_file_dest\" exists." );

    # is it the same?
    my $file = FileHandle->new( $test_file_path, q{r} );
    my $data;
    if ( defined $file ) {
        while (<$file>) {
            $data .= $_;
        }
        undef $file;    # automatically closes the file
    }
    my $blob_hash = 0;
    if ($data) {
        $blob_hash = md5_hex($data);
    }
    $content->view($test_file_dest);
    my $fetched_hash = 1;
    $fetched_hash = md5_hex( $content->{'Message'} );

    ok( $blob_hash eq $fetched_hash, 'I got out what I put in' );

    ok( $content->delete($test_file_dest),
        "Content Test: Content \"$test_file_dest\" deleted successfully." );
    ok( !$content->exists($test_file_dest),
        "Content Test: Content \"$test_file_dest\" should no longer exist." );

    return;
}

#}}}

1;

__END__

