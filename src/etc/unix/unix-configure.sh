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
#   unix_configure -install-dir target

print_usage() {
  echo "Usage: install.sh -install-to <install_dir>"
  exit 1
}

bad_arg() {
  echo "** ERROR: Bad argument. - $1"
  exit 1
}

no_install_dir() {
  echo "** ERROR: No install directory specified."
  exit 1
}

target_is_not_absolute() {
  echo "Install directory $INSTALL_DIR is not an absolute path."
  exit 1
}

props_make_error() {
  echo "** ERROR: Failed to make system.props."
  exit 1
}

copy_error() {
  echo "** ERROR: Failed to copy files."
  exit 1
}

# -- start --

test "$1" != "" || print_usage

while [ "$1" != "" ]; do
  if [ $1 = "-install-to" ]; then
    INSTALL_DIR=${2%/}
    shift 2
  else
    bad_arg $1
  fi
done

test "$INSTALL_DIR" != "" || no_install_dir

test `echo $INSTALL_DIR | cut -c 1` = "/" || target_is_not_absolute

REPLACE_INSTALL_DIR=s:_INSTALL_DIR_:$INSTALL_DIR:g

cat ./etc/unix/install.sh | sed -e "$REPLACE_INSTALL_DIR" > ./install.sh
chmod a+x ./install.sh || copy_error
cat ./etc/unix/link-lib.sh | sed -e "$REPLACE_INSTALL_DIR" > ./link-lib.sh
chmod a+x ./link-lib.sh || copy_error
cat ./etc/unix/copy-lib.sh | sed -e "$REPLACE_INSTALL_DIR" > ./copy-lib.sh
chmod a+x ./copy-lib.sh || copy_error

exit 0
