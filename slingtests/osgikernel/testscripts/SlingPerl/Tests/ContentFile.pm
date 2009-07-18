#!/usr/bin/perl

package Tests::ContentFile;

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
use Data::Dumper;
use JSON::XS;


#}}}
use Image::Magick;

sub compare_hash_and_repo {
    my $args    = shift;
    my $content = $args->{'content'};
    $content->view_file( $args->{'storedpath'} );
    my $message = $content->{'Message'};

    my $fetched_hash = md5_hex($message);

    return $args->{'blob_hash'} eq $fetched_hash;
}

sub compare_localfile_and_repo {
    my $args = shift;
    my $file = FileHandle->new( $args->{'localfile'}, q{r} );
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
    $args->{'blob_hash'} = $blob_hash;
    return compare_hash_and_repo($args);
}

#{{{sub run_regression_test
sub run_regression_test {
    my ( $authn, $verbose, $log ) = @_;
    my $test_file_path = 'data/test_tiny.jpg';
    my $test_file_name = 'test.jpg';
    my $test_file_dest = "blob_$$";
    my $test_crop_dest = "blob_cropped$$";

    # Sling content object:
    my $content = new Sling::Content( $authn, $verbose, $log );

    # Sling ImageCrop object:
    my $cropper = new Sling::ImageCrop( $authn, $verbose, $log );

    # Run tests:
    ok( defined $content,
        'Content Test: Sling Content Object successfully created.' );
    ok(
        $content->upload_file(
            $test_file_path, $test_file_dest, $test_file_name
        ),
"Content Test: Content \"$test_file_name\" uploaded successfully to $test_file_dest."
    );
    ok( $content->exists($test_file_dest),
        "Content Test: Content \"$test_file_dest\" exists." );

    # is it the same?

    ok(
        compare_localfile_and_repo(
            {
                'content'    => $content,
                'localfile'  => $test_file_path,
                'storedpath' => "$test_file_dest/$test_file_name",
            }
        ),
        'Content Test: stored and local file are the same'
    );

    # test image manipulation
    my $dims = [ { 'width' => 256, 'height' => 256 } ];
    my $args = {
         'x'          => 10,
         'y'          => 10,
         'width'      => 100,
         'height'     => 300,
         'urlSaveIn'  => $test_crop_dest,
         'urlToCrop'  => "$test_file_dest/$test_file_name",
         'dimensions' => encode_json $dims,
     };
    my $arrayref_args = [ map {"$_=" . $args->{$_} }  keys %{$args} ];
    carp Dumper $arrayref_args;

    $cropper->crop( $arrayref_args );

    # do it locally
    my $image = Image::Magick->new;
    $image->Read( $test_file_path );
    my $geometry = join q{+},
      ( join q{x}, $args->{'width'}, $args->{'height'} ), $args->{'x'},
      $args->{'y'};
    $image->Crop( 'geometry', $geometry );
    my $scale = join q{x}, $dims->[0]->{'width'}, $dims->[0]->{'height'};
    $image->Thumbnail( 'geometry', "$scale" );
    my $blob = $image->ImageToBlob;
    undef $image;
    my $blob_hash = md5_hex($blob);

    # is it the same?

    ok(
        compare_hash_and_repo(
            {
                'content'    => $content,
                'blob_hash'  => $blob_hash,
                'storedpath' => $test_file_dest . q{/}
                  . $scale . q{_}
                  . $test_file_name,
            }
        ),
        'Content Test: servelet cropped and locally cropped files are the same'
    );

#     ok( $content->delete($test_file_dest),
#         "Content Test: Content \"$test_file_dest\" deleted successfully." );
#     ok( $content->delete($crop_file_dest),
#         "Content Test: Content \"$crop_file_dest\" deleted successfully." );
#     ok( !$content->exists($test_file_dest),
#         "Content Test: Content \"$test_file_dest\" should no longer exist." );
#     ok( !$content->exists($crop_file_dest),
#         "Content Test: Content \"$crop_file_dest\" should no longer exist." );

    return;
}

#}}}

1;

__END__

