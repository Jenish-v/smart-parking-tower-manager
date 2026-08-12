# Facility Module

The facility module owns the configured physical structure of a parking site. Its domain package is
`com.jenish.smartparking.facility.domain`.

## Owned concepts

The module defines facility, floor, zone, and space identities; the size class of each space; and whether a space is
active or out of service. Constructors reject empty structures, invalid identifiers, and duplicate floor, zone, or
space identifiers. Collections are copied and ordered so consumers receive a stable floor, zone, and space sequence.

A space can accept a vehicle when it is active and its size class is equal to or larger than the vehicle's required
size. The size order is small, medium, then large.

The reference layout is six floors, zones A through F on every floor, and spaces 1 through 200 in every zone. The
`ReferenceFacilityLayout` policy verifies this 7,200-space shape without constructing reference data at application
startup. The database fixture is introduced with the persistence milestone.

## Boundary

The facility module does not own occupancy or reservation state. Allocation owns assignment decisions and coordinates
the atomic transition of a space. Parking sessions own vehicle entry and exit history. Reservations own future capacity
claims. Those modules refer to facility identities through application interfaces rather than changing facility data
directly.

Removing a currently occupied space from service requires coordination with allocation and parking sessions. That use
case remains outside the facility domain milestone.
