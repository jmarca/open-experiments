#!/usr/bin/perl

package Sling::ImageCrop;

#{{{imports
use warnings;
use strict;
use Carp;
use lib qw ( .. );
use JSON;
use Sling::Print;
use Sling::Request;
use Sling::ImageCropUtil;
use version; our $VERSION = qv('0.0.1');
use Data::Dumper;
#}}}

#{{{sub new

sub new {
    my ( $class, $authn, $verbose, $log ) = @_;
    if ( !defined $authn ) { croak 'no authn provided!'; }
    my $response;
    my $image_crop = {
        BaseURL        => ${$authn}->{'BaseURL'},
        Authn          => $authn,
        Message        => q{},
        Response       => \$response,
        Verbose        => $verbose,
        'servlet_path' => '/system/image/cropit',
        Log            => $log
    };
    bless $image_crop, $class;
    return $image_crop;
}

#}}}

#{{{sub set_results
sub set_results {
    my ( $image_crop, $message, $response ) = @_;
    $image_crop->{'Message'}  = $message;
    $image_crop->{'Response'} = $response;
    return 1;
}

#}}}

#{{{sub crop
sub crop {
    my ( $self, $properties ) = @_;
    my $res = Sling::Request::request(
        \$self,
        Sling::ImageCropUtil::add_setup(
            $self->{'BaseURL'}, $self->{'servlet_path'}, $properties
        )
    );
    my $success = Sling::ImageCropUtil::add_eval($res);
    my $message = 'ImageCrop crop ';
    $message .= ( $success ? 'succeeded!' : 'failed!' );
    $self->set_results( "$message", $res );
    return $success;
}

#}}}

1;

__END__


=head1 NAME

ImageCrop - image crop related functionality for Sakai implemented over rest
APIs.

=head1 SYNOPSIS

Perl library providing a layer of abstraction to the REST image crop methods

=head1 DESCRIPTION

=head1 SUBROUTINES/METHODS

=head2 new

Create, set up, and return an ImageCrop object.

=head2 crop

Crop an existing image.  Parameters hash must contain the following:
    'x' => integer, the x position of the crop rectangle
    'y' => integer, the y position of the crop rectangle
    'width' => integer the width of the crop rectangle
    'height' => integer the height of the crop rectangle
    'urlSaveIn' => a string path for the cropped image in the repository
    'urlToCrop' => a string path for the image to crop in the repository
    'dimensions' => An arrayref of dimension pairs , jsonified.
                    for example, using JSON::XS, you'd write
                    encode_json [{"width":256,"height":256}]


    my $params = {
        'x'           => 10,
        'y'           => 10,
        'width'       => 100,
        'height'      => 300,
        'urlSaveIn'   => $test_crop_dest,
        'urlToCrop'   => "$test_file_dest/$test_file_name",
        'dimensions'  => encode_json [ { 'width' => 256, 'height' => 256 } ],
      }

=head1 DIAGNOSTICS


=head1 CONFIGURATION AND ENVIRONMENT

Sling::ImageCrop requires no configuration files or environment variables.


=head1 DEPENDENCIES

None.

=head1 INCOMPATIBILITIES

None reported.

=head1 BUGS AND LIMITATIONS

No bugs have been reported.

=head1 AUTHOR

=head1 LICENSE AND COPYRIGHT
This module is free software; you can redistribute it and/or
modify it under the same terms as Perl itself. See L<perlartistic>.


=head1 DISCLAIMER OF WARRANTY

BECAUSE THIS SOFTWARE IS LICENSED FREE OF CHARGE, THERE IS NO WARRANTY
FOR THE SOFTWARE, TO THE EXTENT PERMITTED BY APPLICABLE LAW. EXCEPT WHEN
OTHERWISE STATED IN WRITING THE COPYRIGHT HOLDERS AND/OR OTHER PARTIES
PROVIDE THE SOFTWARE "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER
EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE
ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE SOFTWARE IS WITH
YOU. SHOULD THE SOFTWARE PROVE DEFECTIVE, YOU ASSUME THE COST OF ALL
NECESSARY SERVICING, REPAIR, OR CORRECTION.

IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING
WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MAY MODIFY AND/OR
REDISTRIBUTE THE SOFTWARE AS PERMITTED BY THE ABOVE LICENCE, BE
LIABLE TO YOU FOR DAMAGES, INCLUDING ANY GENERAL, SPECIAL, INCIDENTAL,
OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OR INABILITY TO USE
THE SOFTWARE (INCLUDING BUT NOT LIMITED TO LOSS OF DATA OR DATA BEING
RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD PARTIES OR A
FAILURE OF THE SOFTWARE TO OPERATE WITH ANY OTHER SOFTWARE), EVEN IF
SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF
SUCH DAMAGES.
