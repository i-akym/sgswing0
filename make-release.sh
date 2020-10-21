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

# Make release directory in <target_dir>.
# Usage:
#   (enable execution if needed)
#   ./make-release.sh <target_dir>
# Caution:
#   <target_dir>/release must not exist.

print_usage() {
  echo "Usage: make-release <target_dir>"
  exit 1
}

no_target_dir() {
  echo "** ERROR: $TARGET_DIR does not exist."
  exit 1
}

release_dir_already_exists() {
  echo "** ERROR: $RELEASE_DIR already exists."
  exit 1
}

copy_error() {
  echo "** ERROR: Failed to copy files."
  exit 1
}

# -- start --

test "$1" != "" || print_usage

TARGET_DIR=${1%/}
RELEASE_DIR=$TARGET_DIR/release

test -e $TARGET_DIR || no_target_dir
test ! -e $RELEASE_DIR || release_dir_already_exists

echo "mkdir $RELEASE_DIR"
mkdir $RELEASE_DIR || copy_error

# setup src

echo "mkdir $RELEASE_DIR/src"
mkdir $RELEASE_DIR/src || copy_error

echo "rsync -r --include '*/' --include '*.sg' --exclude '*' src/sgswing $RELEASE_DIR/src"
rsync -r --include '*/' --include '*.sg' --exclude '*' src/sgswing $RELEASE_DIR/src || copy_error
echo "rsync -r --include '*/' --include '*.java' --exclude '*' src/sni_sgswing $RELEASE_DIR/src"
rsync -r --include '*/' --include '*.java' --exclude '*' src/sni_sgswing $RELEASE_DIR/src || copy_error

# setup lib 

echo "mkdir $RELEASE_DIR/lib"
mkdir $RELEASE_DIR/lib || copy_error
echo "rsync -r --include '*/' --include '*.sgm' --exclude '*' src/sgswing $RELEASE_DIR/lib"
rsync -r --include '*/' --include '*.sgm' --exclude '*' src/sgswing $RELEASE_DIR/lib || copy_error
echo "rsync -r --include '*/' --include '*.class' --exclude '*' src/sni_sgswing $RELEASE_DIR/lib"
rsync -r --include '*/' --include '*.class' --exclude '*' src/sni_sgswing $RELEASE_DIR/lib || copy_error

# setup doc 

echo "mkdir $RELEASE_DIR/doc"
mkdir $RELEASE_DIR/doc || copy_error
echo "cp src/doc/*.html $RELEASE_DIR/doc/"
cp src/doc/*.html $RELEASE_DIR/doc/ || copy_error

# setup sample 

echo "rsync -r --include '*/' --include '*.sg' --include '*.sgm' --include '*.gif' --exclude '*' src/sample $RELEASE_DIR"
rsync -r --include '*/' --include '*.sg' --include '*.sgm' --include '*.gif' --exclude '*' src/sample $RELEASE_DIR || copy_error

# setup etc
echo "mkdir $RELEASE_DIR/etc"
mkdir $RELEASE_DIR/etc || copy_error
echo "mkdir $RELEASE_DIR/etc/unix"
mkdir $RELEASE_DIR/etc/unix || copy_error
echo "cp src/etc/unix/*.sh $RELEASE_DIR/etc/unix/"
cp src/etc/unix/*.sh $RELEASE_DIR/etc/unix/ || copy_error

# setup root 

echo "cp LICENSE.txt $RELEASE_DIR/"
cp LICENSE.txt $RELEASE_DIR/ || copy_error

# echo "cp src/etc/README*.txt $RELEASE_DIR/"
# cp src/etc/README*.txt $RELEASE_DIR/ || copy_error

# echo "cp src/etc/win/win_configure.bat $RELEASE_DIR/"
# cp src/etc/win/win_configure.bat $RELEASE_DIR/ || copy_error
echo "copy src/etc/unix/unix-configure.sh $RELEASE_DIR/"
cp src/etc/unix/unix-configure.sh $RELEASE_DIR/ || copy_error

# set permission

echo "find $RELEASE_DIR -type d | xargs chmod 755"
find $RELEASE_DIR -type d | xargs chmod 755 || copy_error
echo "find $RELEASE_DIR -type f | xargs chmod 644"
find $RELEASE_DIR -type f | xargs chmod 644 || copy_error
echo "chmod a+x $RELEASE_DIR/*.sh"
chmod a+x $RELEASE_DIR/*.sh || copy_error

# end

exit 0

