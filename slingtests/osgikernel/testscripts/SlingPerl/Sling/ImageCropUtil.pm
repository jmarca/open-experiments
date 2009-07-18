#!/usr/bin/perl
package Sling::ImageCropUtil;
use warnings;
use strict;
use Carp;
use Data::Dumper;
use version; our $VERSION = qv('0.0.1');

use lib qw ( .. );
use Sling::URL;

#{{{sub add_setup
sub add_setup {
    my ( $baseURL, $remoteDest ) = @_;
    if ( !defined $baseURL ) { croak "No base URL provided!"; }
    if ( !defined $remoteDest ) {
        croak "No position or ID to perform action for specified!";
    }
    # bug? in CropItServlet??  does not handle post, only get, but servlet methods
    # annotation is POST ??  nutty
    # return "post $baseURL/$remoteDest $postVariables";
    return "get $baseURL/$remoteDest"; # need to hack to mix the params in the request body
}
#}}}

#{{{sub add_eval
sub add_eval {
    my ( $res ) = @_;
    return ( ${$res}->code =~ /^20(0|1)$/ );
}
#}}}


sub build_base_request {
    my ( $object, $string ) = @_;
    if(!defined $string){ croak "No string defined to turn into request!"  ;}
    if(!  defined $object){croak "No reference to a suitable object supplied!";}
    my $authn = ${$object}->{ 'Authn' };
    die "Object does not reference a suitable auth object" unless defined $authn;
    my $verbose = ${$object}->{ 'Verbose' };
    my $log = ${$object}->{ 'Log' };
    return Sling::Request::string_to_request( $string, $authn, $verbose, $log );
}

sub fire_request {
  my ($object, $request) = @_;
    my $authn = ${$object}->{ 'Authn' };
    my $lwp = ${$authn}->{ 'LWP' };
    my $res = ${$lwp}->request( $request );
    return \$res;
}

1;

__END__

=head1 NAME

ImageCropUtil - Utility library returning strings representing Rest queries
that perform image cropping and scaling in the system.

=head1 VERSION

This document describes Sling::ImageCropUtil version 0.0.3

=head1 SYNOPSIS

Utility library used by ImageCrop to provide REST request strings.

=head1 DESCRIPTION

ImageCropUtil perl library essentially provides the request strings needed to
interact with image crop functionality exposed over the system rest interfaces.

Each interaction has a setup and eval method. setup provides the request,
whilst eval interprets the response to give further information about the
result of performing the request.

=for author to fill in:
    Write a full description of the module and its features here.
    Use subsections (=head2, =head3) as appropriate.


=head1 SUBROUTINES/METHODS

=head2 add_setup

Returns a textual representation of the request needed to add content to the
system.

=head2 add_eval

Check result of adding content.



=head1 DIAGNOSTICS

=for author to fill in:
    List every single error and warning message that the module can
    generate (even the ones that will "never happen"), with a full
    explanation of each problem, one or more likely causes, and any
    suggested remedies.

=over

=item C<< Error message here, perhaps with %s placeholders >>

[Description of error here]

=item C<< Another error message here >>

[Description of error here]

[Et cetera, et cetera]

=back


=head1 CONFIGURATION AND ENVIRONMENT

Sling::ImageCropUtil requires no configuration files or environment variables.


=head1 DEPENDENCIES

Sling::Url



=head1 INCOMPATIBILITIES

None reported.


=head1 BUGS AND LIMITATIONS


No bugs have been reported.

Please report any bugs or feature requests to C<http://groups.google.co.uk/group/sakai-kernel >
or through the web interface at L<http://jira.sakaiproject.org/browse/KERN>



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



