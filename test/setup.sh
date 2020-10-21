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

# Set up for test.
# Usage:
#   (enable execution if needed)
#   ./setup.sh

setup_error() {
  echo "** ERROR."
  cd $TEST_ROOT
  exit 1
}

# -- start --

TEST_ROOT=`pwd`
rm -R -f release || setup_error
rm -R -f inst || setup_error
cd ..
./make-release.sh $TEST_ROOT || setup_error
cd $TEST_ROOT/release
./unix-configure.sh -install-to $TEST_ROOT/inst || setup_error
./install.sh || setup_error
cd $TEST_ROOT
find . -name '*.sh' | xargs chmod a+x || setup_error
exit 0

