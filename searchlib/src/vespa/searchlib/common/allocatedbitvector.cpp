// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "allocatedbitvector.h"
#include <cstring>
#include <cassert>

namespace search {

using vespalib::GenerationHeldBase;
using vespalib::GenerationHeldAlloc;
using vespalib::GenerationHolder;

namespace {

size_t computeCapacity(size_t capacity, size_t allocatedBytes) {
    size_t possibleCapacity = (allocatedBytes * 8) - 1;
    assert(possibleCapacity >= capacity);
    return possibleCapacity;
}

// This is to ensure that we only read size and capacity once during copy
// to ensure that they do not change unexpectedly under our feet due to resizing in different thread.
std::pair<BitVector::Index, BitVector::Index>
extract_size_size(const BitVector & bv) {
    BitVector::Index size = bv.size();
    return std::pair<BitVector::Index, BitVector::Index>(size, size);
}

std::pair<BitVector::Index, BitVector::Index>
extract_size_capacity(const AllocatedBitVector & bv) {
    BitVector::Index size = bv.size();
    BitVector::Index capacity = bv.capacity();
    while (capacity < size) {
        // Since size and capacity might be changed in another thread we need
        // this fallback to avoid inconsistency during shrink.
        std::atomic_thread_fence(std::memory_order_seq_cst);
        size = bv.size();
        capacity = bv.capacity();
    }
    return std::pair<BitVector::Index, BitVector::Index>(size, capacity);
}

}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements) :
    BitVector(),
    _capacityBits(numberOfElements),
    _alloc(allocatePaddedAndAligned(numberOfElements))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    init(_alloc.get(), 0, numberOfElements);
    clear();
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Alloc buffer, size_t offset) :
    BitVector(static_cast<char *>(buffer.get()) + offset, numberOfElements),
    _capacityBits(numberOfElements),
    _alloc(std::move(buffer))
{
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Index capacityBits, const void * rhsBuf, size_t rhsSize) :
    BitVector(),
    _capacityBits(capacityBits),
    _alloc(allocatePaddedAndAligned(0, numberOfElements, capacityBits))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    init(_alloc.get(), 0, numberOfElements);
    clear();
    if (rhsSize > 0) {
        size_t minCount = std::min(static_cast<size_t>(numberOfElements), rhsSize);
        memcpy(getStart(), rhsBuf, numBytes(minCount));
        if (minCount/8 == numberOfElements/8) {
            static_cast<Word *>(getStart())[numWords()-1] &= ~endBits(minCount);
        }
        setBit(size()); // Guard bit
    }
    updateCount();
}

AllocatedBitVector::AllocatedBitVector(const AllocatedBitVector & rhs) :
    AllocatedBitVector(rhs, extract_size_capacity(rhs))
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs) :
    AllocatedBitVector(rhs, extract_size_size(rhs))
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs, std::pair<Index, Index> size_capacity) :
    BitVector(),
    _capacityBits(size_capacity.second),
    _alloc(allocatePaddedAndAligned(0, size_capacity.first, size_capacity.second))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    memcpy(_alloc.get(),  rhs.getStart(), numBytes(size_capacity.first - rhs.getStartIndex()));
    init(_alloc.get(), 0, size_capacity.first);
    setBit(size());
    updateCount();
}

//////////////////////////////////////////////////////////////////////
// Destructor
//////////////////////////////////////////////////////////////////////
AllocatedBitVector::~AllocatedBitVector() = default;

void
AllocatedBitVector::cleanup()
{
    init(nullptr, 0, 0);
    Alloc().swap(_alloc);
    _capacityBits = 0;
}

void
AllocatedBitVector::resize(Index newLength)
{
    _alloc = allocatePaddedAndAligned(newLength);
    _capacityBits = computeCapacity(newLength, _alloc.size());
    init(_alloc.get(), 0, newLength);
    clear();
}

AllocatedBitVector &
AllocatedBitVector::operator=(const AllocatedBitVector & rhs)
{
    AllocatedBitVector tmp(rhs);
    swap(tmp);
    assert(testBit(size()));

    return *this;
}
AllocatedBitVector &
AllocatedBitVector::operator=(const BitVector & rhs)
{
    AllocatedBitVector tmp(rhs);
    swap(tmp);
    assert(testBit(size()));

    return *this;
}

GenerationHeldBase::UP
AllocatedBitVector::grow(Index newSize, Index newCapacity)
{
    assert(newCapacity >= newSize);
    GenerationHeldBase::UP ret;
    if (newCapacity != capacity()) {
        AllocatedBitVector tbv(newSize, newCapacity, _alloc.get(), size());
        if (newSize > size()) {
            tbv.clearBitAndMaintainCount(size());  // Clear old guard bit.
        }
        ret = std::make_unique<GenerationHeldAlloc<Alloc>>(_alloc);
        swap(tbv);
    } else {
        if (newSize > size()) {
            Range clearRange(size(), newSize);
            setSize(newSize);
            clearIntervalNoInvalidation(clearRange);
        } else {
            clearInterval(newSize, size());
            setSize(newSize);
        }
    }
    return ret;
}

} // namespace search
