#!/bin/sh
###########################################################################
# MIT License                                                             #
# Copyright (c) 2020 AKIYAMA Isao                                         #
#                                                                         #
# Permission is hereby granted, free of charge, to any person obtaining   #
# a copy of this software and associated documentation files (the         #
# "Software"), to deal in the Software without restriction, including     #
# without limitation the rights to use, copy, modify, merge, publish,     #
# distribute, sublicense, and/or sell copies of the Software, and to      #
# permit persons to whom the Software is furnished to do so, subject to   #
# the following conditions:                                               #
#                                                                         #
# The above copyright notice and this permission notice shall be          #
# included in all copies or substantial portions of the Software.         #
#                                                                         #
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,         #
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF      #
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  #
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY    #
# CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,    #
# TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE       #
# SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                  #
###########################################################################

# Usage:
#   copy-lib.sh <sango_install_dir>

print_usage() {
  echo "Usage: copy-lib.sh <sango_install_dir>"
  exit 1
}

copy_error() {
  echo "** ERROR: Failed to copy files."
  exit 1
}

# -- start --

INSTALL_DIR=_INSTALL_DIR_

test "$1" != "" || print_usage

SANGO_INSTALL_DIR=${1%/}

rsync -r --include '*/' --include '*.sgm' --exclude '*' --delete $INSTALL_DIR/lib/sgswing $SANGO_INSTALL_DIR/lib || copy_error
echo "rsync -r --include '*/' --include '*.sgm' --exclude '*' --delete $INSTALL_DIR/lib/sgswing $SANGO_INSTALL_DIR/lib"
rsync -r --include '*/' --include '*.class' --exclude '*' --delete $INSTALL_DIR/lib/sni_sgswing $SANGO_INSTALL_DIR/lib || copy_error
echo "rsync -r --include '*/' --include '*.class' --exclude '*' --delete $INSTALL_DIR/lib/sni_sgswing $SANGO_INSTALL_DIR/lib"

exit 0
