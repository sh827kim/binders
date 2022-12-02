package com.spark.binders.service

import com.spark.binders.constants.ExceptionMessages
import com.spark.binders.domain.entity.MeetingMemberMapping
import com.spark.binders.domain.entity.Meetings
import com.spark.binders.domain.repository.*
import com.spark.binders.dto.MeetingsRequest
import com.spark.binders.dto.MeetingsResponse
import com.spark.binders.exception.NotFoundException
import com.spark.binders.exception.UnAuthorizedException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class MeetingsService(
    private val meetingsRepository: MeetingsRepository,
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val meetingsMemberRepository: MeetingMemberMappingRepository,
    private val userRepository: UserRepository,
) {

    private val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun createMeeting(meetingsRequest: MeetingsRequest) = with(meetingsRequest) {
        val group = groupRepository.findByIdOrNull(groupId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_GROUP.message)
        val parsedEndDate = if(endDate.isNullOrBlank()) null else LocalDateTime.parse(endDate, datetimeFormatter)
        val meetings = Meetings(
            group = group,
            meetingName = meetingName,
            isAnonymous = isAnonymous,
            locationInfo = locationInfo,
            latitude = latitude,
            longitude = longitude,
            limitedMemberCount = limitedMemberCount,
            joinedMemberCount = joinedMemberCount,
            cost = cost,
            startDate = LocalDateTime.parse(startDate, datetimeFormatter),
            endDate = parsedEndDate,
        )
        val savedMeetings = meetingsRepository.save(meetings)
        group.addMeeting(meetings)

        MeetingsResponse(savedMeetings)
    }

    fun updateMeeting(meetingId : Long, meetingsRequest: MeetingsRequest) : MeetingsResponse {
        val meeting = meetingsRepository.findByIdOrNull(meetingId) ?: throw NotFoundException()

        return with(meeting) {
            meetingName = meetingsRequest.meetingName ?: meetingName
            locationInfo = meetingsRequest.locationInfo ?: locationInfo
            latitude = meetingsRequest.latitude?: latitude
            longitude = meetingsRequest.longitude ?: longitude
            limitedMemberCount = meetingsRequest.limitedMemberCount ?: limitedMemberCount
            joinedMemberCount = meetingsRequest.joinedMemberCount ?: joinedMemberCount
            cost = meetingsRequest.cost ?: cost
            startDate = LocalDateTime.parse(meetingsRequest.startDate, datetimeFormatter)?: startDate
            endDate = LocalDateTime.parse(meetingsRequest.endDate, datetimeFormatter)?:endDate
            MeetingsResponse(this)
        }
    }

    fun getMeetings(groupId : Long, pageNumber: Int, pageSize: Int) : List<MeetingsResponse> {
        val pageable = PageRequest.of(pageNumber, pageSize)
        val group = groupRepository.findByIdOrNull(groupId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_GROUP.message)
        val meetings = meetingsRepository.findByGroup(group, pageable)

        return meetings.map { MeetingsResponse(it) }
    }

    fun getMeeting(meetingId: Long) : MeetingsResponse {
        val meeting = meetingsRepository.findWithJoinedMembersById(meetingId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_MEETING.message)

        return MeetingsResponse(meeting)
    }

    fun deleteMeeting(meetingId: Long) : Boolean {
        meetingsRepository.deleteById(meetingId)
        return true
    }

    fun joinMeeting(meetingId: Long, nickname: String?, userId: String) : MeetingsResponse {
        val user = userRepository.findByIdOrNull(userId) ?: throw UnAuthorizedException()
        val meetings = meetingsRepository.findWithJoinedMembersById(meetingId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_MEETING.message)
        val groupMember = groupMemberRepository.findByUserAndGroup(user, meetings.group) ?: throw UnAuthorizedException(ExceptionMessages.NOT_JOINED_GROUP.message)


        val joined = MeetingMemberMapping(
            member = groupMember,
            meeting = meetings,
            anonymousNickname = nickname?: groupMember.memberNickname,
        )

        val savedJoinedMember = meetingsMemberRepository.save(joined)

        meetings.joinMeeting(savedJoinedMember)
        groupMember.joinMeeting(savedJoinedMember)

        return MeetingsResponse(meetings)
    }

    fun leaveMeeting(meetingId: Long, userId: String) : Boolean {
        val user = userRepository.findByIdOrNull(userId) ?: throw UnAuthorizedException()
        val meetings = meetingsRepository.findWithJoinedMembersById(meetingId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_MEETING.message)
        val groupMember = groupMemberRepository.findByUserAndGroup(user, meetings.group) ?: throw UnAuthorizedException(ExceptionMessages.NOT_JOINED_GROUP.message)
        val joined =
            meetings.joinedMembers?.first { it.member?.id?.equals(groupMember.id) ?: throw NotFoundException() } ?: throw NotFoundException()

        groupMember.leaveMeeting(joined)
        meetings.leaveMeeting(joined)

        meetingsMemberRepository.delete(joined)
        return true
    }

    fun attendance(meetingId: Long, userId: String) : Boolean {
        val user = userRepository.findByIdOrNull(userId) ?: throw UnAuthorizedException()
        val meetings = meetingsRepository.findWithJoinedMembersById(meetingId) ?: throw NotFoundException(ExceptionMessages.NOT_EXIST_MEETING.message)
        val groupMember = groupMemberRepository.findByUserAndGroup(user, meetings.group) ?: throw UnAuthorizedException(ExceptionMessages.NOT_JOINED_GROUP.message)
        val joined =
            meetings.joinedMembers?.first { it.member?.id?.equals(groupMember.id) ?: throw NotFoundException() } ?: throw NotFoundException()

        joined.attendance = true

        meetingsMemberRepository.save(joined)

        return true
    }
}